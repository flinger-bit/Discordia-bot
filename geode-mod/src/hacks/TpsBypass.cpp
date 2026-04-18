// ── TPS Bypass ────────────────────────────────────────────────────────────
// Hooks GJBaseGameLayer::update to step physics at a fixed TPS instead of
// the variable frame rate. Uses a leftOver accumulator to handle fractional
// steps — exact same technique as real xdBot.

#include "../includes.hpp"
#include <Geode/modify/GJBaseGameLayer.hpp>

using namespace geode::prelude;

class $modify(XDBotTpsBypass, GJBaseGameLayer) {

    void update(float dt) {
        auto& g = Global::get();

        // Only apply bypass inside an active level
        if (!PlayLayer::get()) {
            GJBaseGameLayer::update(dt);
            return;
        }

        bool enabled = g.tpsEnabled &&
                       Mod::get()->getSettingValue<bool>("tps-bypass");

        if (!enabled) {
            GJBaseGameLayer::update(dt);
            return;
        }

        float newDt   = 1.f / g.tps;
        float realDt  = dt + g.leftOver;

        // Guard: if realDt is way ahead, cap it to avoid freezing
        if (realDt > dt && newDt < dt) realDt = dt;

        int steps = static_cast<int>(realDt / newDt);
        g.leftOver = realDt - steps * newDt;

        for (int i = 0; i < steps; ++i) {
            GJBaseGameLayer::update(newDt);
        }
    }
};
