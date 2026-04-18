// ── Speedhack ─────────────────────────────────────────────────────────────
// Scales CCScheduler::update dt for the whole game, and optionally adjusts
// FMOD audio pitch to match the speed. Mirrors xdBot's other.cpp approach.

#include "../includes.hpp"
#include <Geode/modify/CCScheduler.hpp>

using namespace geode::prelude;

class $modify(XDBotSpeedhack, CCScheduler) {

    void update(float dt) {
        auto& g = Global::get();

        bool enabled = g.speedhackEnabled &&
                       Mod::get()->getSettingValue<bool>("speedhack");

        if (!enabled || !PlayLayer::get()) {
            CCScheduler::update(dt);
            return;
        }

        float speed = g.speedhackValue;
        CCScheduler::update(dt * speed);

        // Audio pitch
        if (Mod::get()->getSettingValue<bool>("speedhack-audio")) {
            auto* fmod = FMODAudioEngine::sharedEngine();
            FMOD::ChannelGroup* cg = nullptr;
            if (fmod && fmod->m_system) {
                fmod->m_system->getMasterChannelGroup(&cg);
                if (cg) cg->setPitch(speed);
            }
        }
    }
};
