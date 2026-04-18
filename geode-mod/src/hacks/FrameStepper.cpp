// ── Frame Stepper ─────────────────────────────────────────────────────────
// When enabled, physics only advances when the user presses N.
// Hooks GJBaseGameLayer::update to gate physics steps.

#include "../includes.hpp"
#include <Geode/modify/GJBaseGameLayer.hpp>
#include <Geode/modify/PlayLayer.hpp>

using namespace geode::prelude;

class $modify(XDBotFrameStepLayer, GJBaseGameLayer) {
    void update(float dt) {
        auto& g = Global::get();

        if (!g.frameStepperEnabled ||
            !Mod::get()->getSettingValue<bool>("frame-stepper") ||
            !PlayLayer::get()) {
            GJBaseGameLayer::update(dt);
            return;
        }

        if (!g.stepFrame) return;

        g.stepFrame = false;
        float fixedDt = 1.f / Global::getTPS();
        GJBaseGameLayer::update(fixedDt);
    }
};

class $modify(XDBotFrameStepPlayLayer, PlayLayer) {
    // Handle the N key to advance one frame
    void keyDown(enumKeyCodes key) {
        if (key == enumKeyCodes::KEY_N) {
            auto& g = Global::get();
            if (g.frameStepperEnabled &&
                Mod::get()->getSettingValue<bool>("frame-stepper")) {
                g.stepFrame = true;
                return;
            }
        }
        PlayLayer::keyDown(key);
    }
};
