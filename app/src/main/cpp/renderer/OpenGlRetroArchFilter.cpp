#include "renderer/OpenGlRetroArchFilter.h"

#include <algorithm>
#include <string>

#include "Platform.h"
#include "renderer/RetroArchOutputScale.h"

namespace MelonDSAndroid
{

namespace
{

constexpr melonDS::u32 kNativeScreenWidth = 256;
constexpr melonDS::u32 kNativeScreenHeight = 192;
class GlStateGuard
{
public:
    ~GlStateGuard()
    {
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glBindVertexArray(0);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glUseProgram(0);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, 0);
        glDisable(GL_SCISSOR_TEST);
    }
};

}

OpenGlRetroArchFilter& OpenGlRetroArchFilter::get()
{
    static OpenGlRetroArchFilter instance;
    return instance;
}

void OpenGlRetroArchFilter::setConfig(const Config& newConfig)
{
    std::lock_guard<std::mutex> lock(mutex);

    const bool sameChain = config.presetPath == newConfig.presetPath
        && config.parameterOverrides == newConfig.parameterOverrides;

    if (sameChain
        && config.enabled == newConfig.enabled
        && config.nativeSourceResolution == newConfig.nativeSourceResolution
        && config.maxLayoutWidth == newConfig.maxLayoutWidth
        && config.maxLayoutHeight == newConfig.maxLayoutHeight
        && config.passCount == newConfig.passCount)
    {
        config.clearHistory = newConfig.clearHistory;
        if (newConfig.clearHistory)
            pendingClearHistory = true;
        return;
    }

    config = newConfig;
    if (!sameChain)
        configDirty = true;
    failedConfigKey.clear();
    if (newConfig.clearHistory)
        pendingClearHistory = true;
}

// A native atlas is 256 x 386 per scale step, anything wider carries widescreen wings
static bool isWideAtlas(melonDS::u32 atlasWidth, melonDS::u32 atlasHeight)
{
    return atlasWidth * 386u > atlasHeight * 256u;
}

OpenGlRetroArchFilter::Sizing OpenGlRetroArchFilter::calculateSizing(melonDS::u32 atlasWidth, melonDS::u32 atlasHeight) const
{
    Sizing sizing{};
    sizing.nativeDisplayMode = config.nativeSourceResolution;
    sizing.inputScale = std::max(1u, atlasWidth / kNativeScreenWidth);
    sizing.inputScreenWidth = kNativeScreenWidth * sizing.inputScale;
    sizing.inputScreenHeight = kNativeScreenHeight * sizing.inputScale;
    sizing.inputBottomOffsetY = atlasHeight > sizing.inputScreenHeight
        ? atlasHeight - sizing.inputScreenHeight
        : 0u;

    const RetroArchOutputSize output = computeRetroArchOutputSize(
        config.maxLayoutWidth,
        config.maxLayoutHeight,
        sizing.inputScreenWidth,
        sizing.inputScreenHeight,
        config.passCount);

    sizing.sourceScreenWidth = sizing.nativeDisplayMode ? kNativeScreenWidth : sizing.inputScreenWidth;
    sizing.sourceScreenHeight = sizing.nativeDisplayMode ? kNativeScreenHeight : sizing.inputScreenHeight;
    sizing.outputScreenWidth = output.screenWidth;
    sizing.outputScreenHeight = output.screenHeight;
    sizing.outputAtlasWidth = output.atlasWidth;
    sizing.outputAtlasHeight = output.atlasHeight;
    sizing.outputBottomOffsetY = output.bottomOffsetY;
    sizing.requestedOutputWidth = output.requestedWidth;
    sizing.requestedOutputHeight = output.requestedHeight;
    sizing.clamped = output.clamped;

    return sizing;
}

void OpenGlRetroArchFilter::logSizingIfNeeded(const Sizing& sizing, melonDS::u32 atlasWidth, melonDS::u32 atlasHeight)
{
    std::string key = std::to_string(atlasWidth) + "x" + std::to_string(atlasHeight)
        + "|" + std::to_string(sizing.sourceScreenWidth) + "x" + std::to_string(sizing.sourceScreenHeight)
        + "|" + std::to_string(sizing.outputScreenWidth) + "x" + std::to_string(sizing.outputScreenHeight)
        + "|" + (sizing.nativeDisplayMode ? "native" : "ir");
    if (key == lastSizingLogKey)
        return;
    lastSizingLogKey = std::move(key);

    melonDS::Platform::Log(
        melonDS::Platform::LogLevel::Info,
        "OpenGlPresenter[RetroArch]: sizing mode=%s atlas=%ux%u input=%ux%u source=%ux%u output=%ux%u outAtlas=%ux%u layout=%ux%u requested=%ux%u%s",
        sizing.nativeDisplayMode ? "native" : "internal",
        atlasWidth,
        atlasHeight,
        sizing.inputScreenWidth,
        sizing.inputScreenHeight,
        sizing.sourceScreenWidth,
        sizing.sourceScreenHeight,
        sizing.outputScreenWidth,
        sizing.outputScreenHeight,
        sizing.outputAtlasWidth,
        sizing.outputAtlasHeight,
        config.maxLayoutWidth,
        config.maxLayoutHeight,
        sizing.requestedOutputWidth,
        sizing.requestedOutputHeight,
        sizing.clamped ? " (clamped)" : "");
}

std::string OpenGlRetroArchFilter::makeConfigKey(melonDS::u32 atlasWidth, melonDS::u32 atlasHeight) const
{
    std::string key = config.presetPath;
    key += '|';
    key += std::to_string(atlasWidth);
    key += 'x';
    key += std::to_string(atlasHeight);
    key += config.nativeSourceResolution ? "|native" : "|ir";
    key += '|';
    key += std::to_string(config.maxLayoutWidth);
    key += 'x';
    key += std::to_string(config.maxLayoutHeight);
    key += "|p";
    key += std::to_string(config.passCount);
    for (const auto& [name, value] : config.parameterOverrides)
    {
        key += '|';
        key += name;
        key += '=';
        key += std::to_string(value);
    }
    return key;
}

GLuint OpenGlRetroArchFilter::runFilter(GLuint sourceTexture, melonDS::u32 atlasWidth, melonDS::u32 atlasHeight)
{
    std::lock_guard<std::mutex> lock(mutex);

    if (!config.enabled || config.presetPath.empty() || sourceTexture == 0 || atlasWidth == 0 || atlasHeight == 0)
        return 0;

    if (atlasWidth < kNativeScreenWidth || atlasHeight < kNativeScreenHeight)
        return 0;

    // TODO: run presets on widescreen frames. The chains assume a 256 wide screen, so leave them unfiltered for now
    if (isWideAtlas(atlasWidth, atlasHeight))
        return 0;

    const Sizing sizing = calculateSizing(atlasWidth, atlasHeight);
    if (sizing.sourceScreenWidth == 0 || sizing.outputScreenWidth == 0)
        return 0;

    const std::string configKey = makeConfigKey(atlasWidth, atlasHeight);
    if (!failedConfigKey.empty() && failedConfigKey == configKey)
        return 0;

    GlStateGuard stateGuard;

    if (!ensureChains(sizing, configKey, atlasWidth, atlasHeight))
        return 0;

    const bool downsampling = sizing.sourceScreenWidth < sizing.inputScreenWidth;
    if (!drawScreenInput(
            sourceTexture, 0, 0, sizing.inputScreenWidth, sizing.inputScreenHeight,
            atlasWidth, atlasHeight, topInput, downsampling)
        || !drawScreenInput(
            sourceTexture,
            0, sizing.inputBottomOffsetY, sizing.inputScreenWidth, sizing.inputBottomOffsetY + sizing.inputScreenHeight,
            atlasWidth, atlasHeight, bottomInput, downsampling))
    {
        return 0;
    }

    frameCount++;
    const bool clearHistory = pendingClearHistory || frameCount <= 1;
    pendingClearHistory = false;
    constexpr melonDS::u32 kFrametimeDeltaMs = 16;
    if (!topChain.renderFrame(topInput.texture, topOutput.texture, frameCount, clearHistory, kFrametimeDeltaMs)
        || !bottomChain.renderFrame(bottomInput.texture, bottomOutput.texture, frameCount, clearHistory, kFrametimeDeltaMs))
    {
        failedConfigKey = configKey;
        melonDS::Platform::Log(
            melonDS::Platform::LogLevel::Warn,
            "OpenGlPresenter[RetroArch]: frame failed for %s; presenting unfiltered",
            config.presetPath.c_str());
        return 0;
    }

    blitRegion(
        topOutput.texture, 0, 0, sizing.outputScreenWidth, sizing.outputScreenHeight,
        atlasOutput.texture, 0, 0, sizing.outputScreenWidth, sizing.outputScreenHeight,
        false);
    blitRegion(
        bottomOutput.texture, 0, 0, sizing.outputScreenWidth, sizing.outputScreenHeight,
        atlasOutput.texture,
        0, sizing.outputBottomOffsetY, sizing.outputScreenWidth, sizing.outputBottomOffsetY + sizing.outputScreenHeight,
        false);

    return atlasOutput.texture;
}

bool OpenGlRetroArchFilter::prewarm(melonDS::u32 atlasWidth, melonDS::u32 atlasHeight)
{
    std::lock_guard<std::mutex> lock(mutex);

    if (!config.enabled || config.presetPath.empty() || atlasWidth == 0 || atlasHeight == 0)
        return true;

    if (atlasWidth < kNativeScreenWidth || atlasHeight < kNativeScreenHeight)
        return true;

    if (isWideAtlas(atlasWidth, atlasHeight))
        return true;

    const Sizing sizing = calculateSizing(atlasWidth, atlasHeight);
    if (sizing.sourceScreenWidth == 0 || sizing.outputScreenWidth == 0)
        return true;

    const std::string configKey = makeConfigKey(atlasWidth, atlasHeight);
    if (!failedConfigKey.empty() && failedConfigKey == configKey)
        return false;

    melonDS::Platform::Log(
        melonDS::Platform::LogLevel::Info,
        "OpenGlPresenter[RetroArch]: prewarming %s for atlas %ux%u",
        config.presetPath.c_str(),
        atlasWidth,
        atlasHeight);

    GlStateGuard stateGuard;
    const bool ready = ensureChains(sizing, configKey, atlasWidth, atlasHeight);
    glFinish();
    return ready;
}

bool OpenGlRetroArchFilter::ensureChains(
    const Sizing& sizing,
    const std::string& configKey,
    melonDS::u32 atlasWidth,
    melonDS::u32 atlasHeight)
{
    if (configDirty)
    {
        topChain.shutdown();
        bottomChain.shutdown();
        configDirty = false;
    }

    logSizingIfNeeded(sizing, atlasWidth, atlasHeight);

    if (!ensureResources(sizing))
    {
        if (lastResourceFailureKey != configKey)
        {
            lastResourceFailureKey = configKey;
            melonDS::Platform::Log(
                melonDS::Platform::LogLevel::Warn,
                "OpenGlPresenter[RetroArch]: failed to allocate resources for %ux%u; presenting unfiltered",
                atlasWidth,
                atlasHeight);
        }
        return false;
    }

    lastResourceFailureKey.clear();

    if (!topChain.configure(
            config.presetPath,
            sizing.sourceScreenWidth, sizing.sourceScreenHeight,
            sizing.outputScreenWidth, sizing.outputScreenHeight,
            config.parameterOverrides)
        || !bottomChain.configure(
            config.presetPath,
            sizing.sourceScreenWidth, sizing.sourceScreenHeight,
            sizing.outputScreenWidth, sizing.outputScreenHeight,
            config.parameterOverrides))
    {
        failedConfigKey = configKey;
        melonDS::Platform::Log(
            melonDS::Platform::LogLevel::Warn,
            "OpenGlPresenter[RetroArch]: preset failed to compile (%s); presenting unfiltered",
            config.presetPath.c_str());
        return false;
    }

    return true;
}

bool OpenGlRetroArchFilter::ensureResources(const Sizing& sizing)
{
    if (readFbo == 0)
        glGenFramebuffers(1, &readFbo);
    if (drawFbo == 0)
        glGenFramebuffers(1, &drawFbo);
    if (readFbo == 0 || drawFbo == 0)
        return false;

    GLint maxTextureSize = 0;
    glGetIntegerv(GL_MAX_TEXTURE_SIZE, &maxTextureSize);
    if (maxTextureSize > 0
        && (static_cast<GLint>(sizing.outputAtlasWidth) > maxTextureSize
            || static_cast<GLint>(sizing.outputAtlasHeight) > maxTextureSize))
    {
        return false;
    }

    if (!ensureChannelSwapProgram())
    {
        return false;
    }

    return ensureImage(topInput, sizing.sourceScreenWidth, sizing.sourceScreenHeight, false)
        && ensureImage(topOutput, sizing.outputScreenWidth, sizing.outputScreenHeight, false)
        && ensureImage(bottomInput, sizing.sourceScreenWidth, sizing.sourceScreenHeight, false)
        && ensureImage(bottomOutput, sizing.outputScreenWidth, sizing.outputScreenHeight, false)
        && ensureImage(atlasOutput, sizing.outputAtlasWidth, sizing.outputAtlasHeight, true);
}

bool OpenGlRetroArchFilter::ensureImage(ImageResource& image, melonDS::u32 width, melonDS::u32 height, bool swapRedAndBlue)
{
    if (image.texture != 0 && image.width == width && image.height == height)
        return true;

    destroyImage(image);

    while (glGetError() != GL_NO_ERROR)
    {
    }

    glGenTextures(1, &image.texture);
    if (image.texture == 0)
        return false;

    glBindTexture(GL_TEXTURE_2D, image.texture);
    glTexStorage2D(GL_TEXTURE_2D, 1, GL_RGBA8, static_cast<GLsizei>(width), static_cast<GLsizei>(height));
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    if (swapRedAndBlue)
    {
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_SWIZZLE_R, GL_BLUE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_SWIZZLE_B, GL_RED);
    }
    glBindTexture(GL_TEXTURE_2D, 0);

    if (glGetError() != GL_NO_ERROR)
    {
        destroyImage(image);
        return false;
    }

    image.width = width;
    image.height = height;
    return true;
}

void OpenGlRetroArchFilter::destroyImage(ImageResource& image)
{
    if (image.texture != 0)
        glDeleteTextures(1, &image.texture);
    image.texture = 0;
    image.width = 0;
    image.height = 0;
}

bool OpenGlRetroArchFilter::ensureChannelSwapProgram()
{
    if (channelSwapProgram != 0)
    {
        return true;
    }

    static const char* kVertexSource =
        "#version 300 es\n"
        "uniform vec4 uUvRect;\n"
        "out vec2 vUv;\n"
        "void main()\n"
        "{\n"
        "    vec2 p = vec2(float((gl_VertexID << 1) & 2), float(gl_VertexID & 2));\n"
        "    vUv = uUvRect.xy + p * uUvRect.zw;\n"
        "    gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);\n"
        "}\n";
    static const char* kFragmentSource =
        "#version 300 es\n"
        "precision highp float;\n"
        "uniform sampler2D uSource;\n"
        "in vec2 vUv;\n"
        "out vec4 fragColor;\n"
        "void main()\n"
        "{\n"
        "    fragColor = vec4(texture(uSource, vUv).bgr, 1.0);\n"
        "}\n";

    auto compile = [](GLenum type, const char* source) -> GLuint {
        GLuint shader = glCreateShader(type);
        if (shader == 0)
            return 0;
        glShaderSource(shader, 1, &source, nullptr);
        glCompileShader(shader);
        GLint compiled = GL_FALSE;
        glGetShaderiv(shader, GL_COMPILE_STATUS, &compiled);
        if (compiled != GL_TRUE)
        {
            char log[512] = {};
            glGetShaderInfoLog(shader, sizeof(log) - 1, nullptr, log);
            melonDS::Platform::Log(
                melonDS::Platform::LogLevel::Error,
                "OpenGlPresenter[RetroArch]: channel swap shader failed: %s",
                log);
            glDeleteShader(shader);
            return 0;
        }
        return shader;
    };

    const GLuint vertex = compile(GL_VERTEX_SHADER, kVertexSource);
    const GLuint fragment = vertex != 0 ? compile(GL_FRAGMENT_SHADER, kFragmentSource) : 0;
    if (vertex == 0 || fragment == 0)
    {
        if (vertex != 0)
            glDeleteShader(vertex);
        if (fragment != 0)
            glDeleteShader(fragment);
        return false;
    }

    const GLuint program = glCreateProgram();
    glAttachShader(program, vertex);
    glAttachShader(program, fragment);
    glLinkProgram(program);
    glDeleteShader(vertex);
    glDeleteShader(fragment);

    GLint linked = GL_FALSE;
    glGetProgramiv(program, GL_LINK_STATUS, &linked);
    if (linked != GL_TRUE)
    {
        char log[512] = {};
        glGetProgramInfoLog(program, sizeof(log) - 1, nullptr, log);
        melonDS::Platform::Log(
            melonDS::Platform::LogLevel::Error,
            "OpenGlPresenter[RetroArch]: channel swap link failed: %s",
            log);
        glDeleteProgram(program);
        return false;
    }

    glGenVertexArrays(1, &channelSwapVao);
    if (channelSwapVao == 0)
    {
        glDeleteProgram(program);
        return false;
    }

    channelSwapProgram = program;
    channelSwapUvRect = glGetUniformLocation(program, "uUvRect");
    glUseProgram(program);
    glUniform1i(glGetUniformLocation(program, "uSource"), 0);
    glUseProgram(0);
    return true;
}

bool OpenGlRetroArchFilter::drawScreenInput(
    GLuint sourceTexture,
    melonDS::u32 srcX0, melonDS::u32 srcY0, melonDS::u32 srcX1, melonDS::u32 srcY1,
    melonDS::u32 sourceWidth, melonDS::u32 sourceHeight,
    const ImageResource& destination,
    bool linear)
{
    if (channelSwapProgram == 0 || destination.texture == 0 || sourceWidth == 0 || sourceHeight == 0)
    {
        return false;
    }

    glBindFramebuffer(GL_DRAW_FRAMEBUFFER, drawFbo);
    glFramebufferTexture2D(GL_DRAW_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, destination.texture, 0);
    const GLenum drawBuffers[] = { GL_COLOR_ATTACHMENT0 };
    glDrawBuffers(1, drawBuffers);

    glDisable(GL_SCISSOR_TEST);
    glDisable(GL_BLEND);
    glDisable(GL_DEPTH_TEST);
    glDisable(GL_CULL_FACE);
    glViewport(0, 0, static_cast<GLsizei>(destination.width), static_cast<GLsizei>(destination.height));

    glUseProgram(channelSwapProgram);
    glUniform4f(
        channelSwapUvRect,
        static_cast<float>(srcX0) / static_cast<float>(sourceWidth),
        static_cast<float>(srcY0) / static_cast<float>(sourceHeight),
        static_cast<float>(srcX1 - srcX0) / static_cast<float>(sourceWidth),
        static_cast<float>(srcY1 - srcY0) / static_cast<float>(sourceHeight));

    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, sourceTexture);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, linear ? GL_LINEAR : GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, linear ? GL_LINEAR : GL_NEAREST);

    glBindVertexArray(channelSwapVao);
    glDrawArrays(GL_TRIANGLES, 0, 3);
    glBindVertexArray(0);

    glFramebufferTexture2D(GL_DRAW_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, 0, 0);
    glBindFramebuffer(GL_DRAW_FRAMEBUFFER, 0);
    glUseProgram(0);
    return true;
}

void OpenGlRetroArchFilter::blitRegion(
    GLuint sourceTexture,
    melonDS::u32 srcX0, melonDS::u32 srcY0, melonDS::u32 srcX1, melonDS::u32 srcY1,
    GLuint destinationTexture,
    melonDS::u32 dstX0, melonDS::u32 dstY0, melonDS::u32 dstX1, melonDS::u32 dstY1,
    bool linear)
{
    glBindFramebuffer(GL_READ_FRAMEBUFFER, readFbo);
    glFramebufferTexture2D(GL_READ_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, sourceTexture, 0);
    glReadBuffer(GL_COLOR_ATTACHMENT0);

    glBindFramebuffer(GL_DRAW_FRAMEBUFFER, drawFbo);
    glFramebufferTexture2D(GL_DRAW_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, destinationTexture, 0);
    const GLenum drawBuffers[] = { GL_COLOR_ATTACHMENT0 };
    glDrawBuffers(1, drawBuffers);

    glDisable(GL_SCISSOR_TEST);
    glBlitFramebuffer(
        static_cast<GLint>(srcX0), static_cast<GLint>(srcY0), static_cast<GLint>(srcX1), static_cast<GLint>(srcY1),
        static_cast<GLint>(dstX0), static_cast<GLint>(dstY0), static_cast<GLint>(dstX1), static_cast<GLint>(dstY1),
        GL_COLOR_BUFFER_BIT,
        linear ? GL_LINEAR : GL_NEAREST);

    glFramebufferTexture2D(GL_READ_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, 0, 0);
    glFramebufferTexture2D(GL_DRAW_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, 0, 0);
    glBindFramebuffer(GL_READ_FRAMEBUFFER, 0);
    glBindFramebuffer(GL_DRAW_FRAMEBUFFER, 0);
}

void OpenGlRetroArchFilter::release()
{
    std::lock_guard<std::mutex> lock(mutex);

    topChain.shutdown();
    bottomChain.shutdown();
    destroyImage(topInput);
    destroyImage(topOutput);
    destroyImage(bottomInput);
    destroyImage(bottomOutput);
    destroyImage(atlasOutput);

    if (readFbo != 0)
    {
        glDeleteFramebuffers(1, &readFbo);
        readFbo = 0;
    }
    if (drawFbo != 0)
    {
        glDeleteFramebuffers(1, &drawFbo);
        drawFbo = 0;
    }
    if (channelSwapProgram != 0)
    {
        glDeleteProgram(channelSwapProgram);
        channelSwapProgram = 0;
        channelSwapUvRect = -1;
    }
    if (channelSwapVao != 0)
    {
        glDeleteVertexArrays(1, &channelSwapVao);
        channelSwapVao = 0;
    }

    frameCount = 0;
    configDirty = true;
    failedConfigKey.clear();
}

}
