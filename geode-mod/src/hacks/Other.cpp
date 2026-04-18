// ── Other hacks & setting listeners ──────────────────────────────────────
// Syncs mod settings into Global state so other hooks can read them cheaply.

#include "../includes.hpp"

using namespace geode::prelude;

$execute {
    auto& g = Global::get();

    // Keep Global in sync with settings changes
    listenForSettingChanges("tps-bypass", [&](bool val) {
        g.tpsEnabled = val;
    });
    listenForSettingChanges("tps-value", [&](double val) {
        g.tps = static_cast<float>(val);
    });
    listenForSettingChanges("speedhack", [&](bool val) {
        g.speedhackEnabled = val;
    });
    listenForSettingChanges("speedhack-value", [&](double val) {
        g.speedhackValue = static_cast<float>(val);
    });
    listenForSettingChanges("noclip", [&](bool val) {
        g.noclipEnabled = val;
    });
    listenForSettingChanges("clickbot", [&](bool val) {
        g.clickbotEnabled = val;
    });
    listenForSettingChanges("frame-stepper", [&](bool val) {
        g.frameStepperEnabled = val;
    });

    // Initialise from current saved settings
    g.tpsEnabled          = Mod::get()->getSettingValue<bool>("tps-bypass");
    g.tps                 = Mod::get()->getSettingValue<float>("tps-value");
    g.speedhackEnabled    = Mod::get()->getSettingValue<bool>("speedhack");
    g.speedhackValue      = Mod::get()->getSettingValue<float>("speedhack-value");
    g.noclipEnabled       = Mod::get()->getSettingValue<bool>("noclip");
    g.clickbotEnabled     = Mod::get()->getSettingValue<bool>("clickbot");
    g.frameStepperEnabled = Mod::get()->getSettingValue<bool>("frame-stepper");
}
