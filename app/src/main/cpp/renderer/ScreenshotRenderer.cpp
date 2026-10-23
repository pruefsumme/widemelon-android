#include "ScreenshotRenderer.h"
#include <algorithm>
#include <chrono>
#include "MelonLog.h"
#include "GPU.h"

namespace MelonDSAndroid
{

ScreenshotRenderer::ScreenshotRenderer(u32* screenshotBuffer)
{
    this->screenshotBuffer = screenshotBuffer;
    this->screenshotRequested = false;
    this->screenshotSucceeded = false;
    this->stopped = false;
}

void ScreenshotRenderer::init()
{
    if (initialized)
        return;

    setupFrameBuffer();
    setupShaders();
    setupVertexBuffers();
    initialized = true;
}

void ScreenshotRenderer::renderScreenshot(GPU* gpu, Renderer renderer, Frame* renderFrame)
{
    if (renderer == Renderer::Vulkan)
    {
        constexpr size_t screenshotPixelCount = static_cast<size_t>(256) * static_cast<size_t>(192) * 2;
        std::fill_n(screenshotBuffer, screenshotPixelCount, 0u);
        LOG_ERROR("Vulkan", "Vulkan screenshot capture must use VulkanOutput readback path");
        notifyScreenshotReady(false);
        return;
    }

    if (renderer == Renderer::Software)
    {
        int frontBuffer = gpu->FrontBuffer;
        memcpy(screenshotBuffer, gpu->Framebuffer[frontBuffer][0].get(), 256 * 192 * 4);
        memcpy(&screenshotBuffer[256 * 192], gpu->Framebuffer[frontBuffer][1].get(), 256 * 192 * 4);
    }
    else
    {
        glDisable(GL_SCISSOR_TEST);
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_STENCIL_TEST);
        glDisable(GL_BLEND);
        glViewport(0, 0, SCREENSHOT_WIDTH, SCREENSHOT_HEIGHT);
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, frameBuffer);
        glUseProgram(screenshotRenderShader);

        // Render the frame texture into the FBO
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, renderFrame->frameTexture);

        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glEnableVertexAttribArray(posAttribLocation);
        glEnableVertexAttribArray(texCoordAttribLocation);
        glVertexAttribPointer(posAttribLocation, 2, GL_FLOAT, GL_FALSE, 4 * sizeof(float), (void*)0);
        glVertexAttribPointer(texCoordAttribLocation, 2, GL_FLOAT, GL_FALSE, 4 * sizeof(float), (void*)(2 * sizeof(float)));
        glUniform1i(textureUniformLocation, 0);

        // Screenshots and thumbnails stay at the native 256 width, so wide frames only keep the middle
        float uvScaleX = 1.0f;
        if (renderFrame->width > 0 && renderFrame->height > 0)
            uvScaleX = std::min(1.0f, (256.0f * renderFrame->height) / (386.0f * renderFrame->width));
        glUniform1f(uvScaleXUniformLocation, uvScaleX);

        glDrawArrays(GL_TRIANGLES, 0, 12);

        glBindBuffer(GL_PIXEL_PACK_BUFFER, 0);
        glBindFramebuffer(GL_READ_FRAMEBUFFER, frameBuffer);
        glReadBuffer(GL_COLOR_ATTACHMENT0);
        glReadPixels(0, 0, SCREENSHOT_WIDTH, SCREENSHOT_HEIGHT, GL_RGBA, GL_UNSIGNED_BYTE, screenshotBuffer);

        glBindVertexArray(0);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindFramebuffer(GL_READ_FRAMEBUFFER, 0);
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, 0);
    }

    notifyScreenshotReady();
}

u32* ScreenshotRenderer::getScreenshot()
{
    return screenshotBuffer;
}

bool ScreenshotRenderer::takeScreenshot()
{
    std::unique_lock lock(screenshotMutex);
    if (stopped)
        return false;
    screenshotSucceeded = false;
    screenshotRequested = true;
    const bool completed = screenshotCondition.wait_for(lock, std::chrono::seconds(2), [this] {
        return !screenshotRequested || stopped;
    });
    screenshotRequested = false;
    return completed && !stopped && screenshotSucceeded;
}

bool ScreenshotRenderer::isScreenshotPending()
{
    std::lock_guard lock(screenshotMutex);
    return screenshotRequested;
}

void ScreenshotRenderer::setupFrameBuffer()
{
    glGenFramebuffers(1, &frameBuffer);
    glGenTextures(1, &bufferTexture);

    glBindTexture(GL_TEXTURE_2D, bufferTexture);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, SCREENSHOT_WIDTH, SCREENSHOT_HEIGHT, 0, GL_RGBA, GL_UNSIGNED_BYTE, NULL);

    glBindFramebuffer(GL_FRAMEBUFFER, frameBuffer);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, bufferTexture, 0);

    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    glBindTexture(GL_TEXTURE_2D, 0);
}

void ScreenshotRenderer::setupShaders()
{
    const char *vertexShaderSource =
            "#version 100\n"
            "attribute vec2 aTexCoord;\n"
            "attribute vec2 aPosition;\n"
            "uniform float uUvScaleX;\n"
            "varying vec2 vTexCoord;\n"
            "void main() {\n"
            "    gl_Position = vec4(aPosition, 0.0, 1.0);\n"
            "    vTexCoord = vec2(0.5 + (aTexCoord.x - 0.5) * uUvScaleX, aTexCoord.y);\n"
            "}\n";
    const char *fragmentShaderSource =
            "#version 100\n"
            "precision mediump float;\n"
            "precision mediump sampler2D;\n"
            "varying vec2 vTexCoord;\n"
            "uniform sampler2D sTexture;\n"
            "void main() {\n"
            "    gl_FragColor = texture2D(sTexture, vTexCoord);\n"
            "}\n";

    GLint shaderResult;
    screenshotRenderVertexShader = glCreateShader(GL_VERTEX_SHADER);
    GLint len = strlen(vertexShaderSource);
    glShaderSource(screenshotRenderVertexShader, 1, &vertexShaderSource, &len);
    glCompileShader(screenshotRenderVertexShader);
    glGetShaderiv(screenshotRenderVertexShader, GL_COMPILE_STATUS, &shaderResult);
    if (shaderResult != GL_TRUE)
    {
        glGetShaderiv(screenshotRenderVertexShader, GL_INFO_LOG_LENGTH, &shaderResult);
        if (shaderResult < 1) shaderResult = 1024;
        char* log = new char[shaderResult + 1];
        glGetShaderInfoLog(screenshotRenderVertexShader, shaderResult + 1, NULL, log);
        LOG_ERROR("OpenGL", "Failed to compile vertex shader: %s", log);
        delete[] log;

        glDeleteShader(screenshotRenderVertexShader);

        return;
    }

    screenshotRenderFragmentShader = glCreateShader(GL_FRAGMENT_SHADER);
    len = strlen(fragmentShaderSource);
    glShaderSource(screenshotRenderFragmentShader, 1, &fragmentShaderSource, &len);
    glCompileShader(screenshotRenderFragmentShader);
    glGetShaderiv(screenshotRenderFragmentShader, GL_COMPILE_STATUS, &shaderResult);
    if (shaderResult != GL_TRUE)
    {
        glGetShaderiv(screenshotRenderFragmentShader, GL_INFO_LOG_LENGTH, &shaderResult);
        if (shaderResult < 1) shaderResult = 1024;
        char* log = new char[shaderResult + 1];
        glGetShaderInfoLog(screenshotRenderFragmentShader, shaderResult + 1, NULL, log);
        LOG_ERROR("OpenGL", "Failed to compile fragment shader: %s", log);
        delete[] log;

        glDeleteShader(screenshotRenderVertexShader);
        glDeleteShader(screenshotRenderFragmentShader);

        return;
    }

    screenshotRenderShader = glCreateProgram();
    glAttachShader(screenshotRenderShader, screenshotRenderVertexShader);
    glAttachShader(screenshotRenderShader, screenshotRenderFragmentShader);
    glLinkProgram(screenshotRenderShader);
    glGetProgramiv(screenshotRenderShader, GL_LINK_STATUS, &shaderResult);
    if (shaderResult != GL_TRUE)
    {
        glGetProgramiv(screenshotRenderShader, GL_INFO_LOG_LENGTH, &shaderResult);
        if (shaderResult < 1) shaderResult = 1024;
        char* log = new char[shaderResult + 1];
        glGetProgramInfoLog(screenshotRenderShader, shaderResult + 1, NULL, log);
        LOG_ERROR("OpenGL", "Failed to link shader program: %s", log);
        delete[] log;

        glDeleteShader(screenshotRenderVertexShader);
        glDeleteShader(screenshotRenderFragmentShader);
        glDeleteProgram(screenshotRenderShader);

        return;
    }

    posAttribLocation = glGetAttribLocation(screenshotRenderShader, "aPosition");
    texCoordAttribLocation = glGetAttribLocation(screenshotRenderShader, "aTexCoord");
    textureUniformLocation = glGetUniformLocation(screenshotRenderShader, "sTexture");
    uvScaleXUniformLocation = glGetUniformLocation(screenshotRenderShader, "uUvScaleX");
}

void ScreenshotRenderer::setupVertexBuffers()
{
    float margin = 1.0f / 192.0f;
    // Image is vertically flipped
    const float vertices[] = {
        //Position        // UV
        -1.0f,  -1.0f,    0.0f, 0.0f,
        -1.0f,  0.0f,     0.0f, 0.5f - margin,
        1.0f,  0.0f,      1.0f, 0.5f - margin,

        -1.0f,  -1.0f,    0.0f, 0.0f,
        1.0f,  0.0f,      1.0f, 0.5f - margin,
        1.0f, -1.0f,      1.0f, 0.0f,

        -1.0f, 0.0f,      0.0f, 0.5f + margin,
        -1.0f, 1.0f,      0.0f, 1.0f,
        1.0f, 1.0f,       1.0f, 1.0f,

        -1.0f, 0.0f,      0.0f, 0.5f + margin,
        1.0f, 1.0f,       1.0f, 1.0f,
        1.0f, 0.0f,       1.0f, 0.5f + margin,
    };

    glGenBuffers(1, &vbo);
    glGenVertexArrays(1, &vao);

    glBindVertexArray(vao);
    glBindBuffer(GL_ARRAY_BUFFER, vbo);
    glBufferData(GL_ARRAY_BUFFER, sizeof(vertices), vertices, GL_STATIC_DRAW);
}

void ScreenshotRenderer::notifyScreenshotReady(bool success)
{
    std::lock_guard lock(screenshotMutex);
    screenshotSucceeded = success;
    screenshotRequested = false;
    screenshotCondition.notify_all();
}

void ScreenshotRenderer::cleanup()
{
    {
        std::lock_guard lock(screenshotMutex);
        stopped = true;
        screenshotCondition.notify_all();
    }
    if (!initialized)
        return;
    glDeleteShader(screenshotRenderVertexShader);
    glDeleteShader(screenshotRenderFragmentShader);
    glDeleteProgram(screenshotRenderShader);
    glDeleteTextures(1, &bufferTexture);
    glDeleteFramebuffers(1, &frameBuffer);
    glDeleteBuffers(1, &vbo);
    glDeleteVertexArrays(1, &vao);
    initialized = false;
}

}
