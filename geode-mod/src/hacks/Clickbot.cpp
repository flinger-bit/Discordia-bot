#include "Clickbot.hpp"
#include "../includes.hpp"
#include <Geode/modify/FMODAudioEngine.hpp>

using namespace geode::prelude;

void Clickbot::playSound(const std::string& filename, float volume) {
    auto path = Mod::get()->getResourcesDir() / filename;
    if (!std::filesystem::exists(path)) return;

    FMODAudioEngine::sharedEngine()->playEffect(path.string(), 1.f, 0.f, volume);
}

void Clickbot::onInput(bool hold, int button, bool player2) {
    auto& g = Global::get();

    if (!g.clickbotEnabled) return;
    if (!Mod::get()->getSettingValue<bool>("clickbot")) return;

    // If "only while playing" is set, skip during recording/disabled
    if (Mod::get()->getSettingValue<bool>("clickbot-playing-only") &&
        g.state != BotState::Playing) return;

    float volume = Mod::get()->getSettingValue<int>("clickbot-volume") / 100.f;

    // Only play for button 1 (jump) for now — expand for left/right as desired
    if (button != 1) return;

    std::string file = hold ? "hold_click.mp3" : "release_click.mp3";
    playSound(file, volume);
}
