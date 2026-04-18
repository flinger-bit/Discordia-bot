// ── Noclip + Auto-Respawn ────────────────────────────────────────────────
// Hooks PlayerObject::playerDestroyed to skip death,
// and PlayLayer hooks for auto-respawn.

#include "../includes.hpp"
#include <Geode/modify/PlayerObject.hpp>
#include <Geode/modify/PlayLayer.hpp>

using namespace geode::prelude;

class $modify(XDBotNoclipPlayer, PlayerObject) {

    // playerDestroyed is called when the player dies; returning early skips death
    void playerDestroyed(bool p0) {
        auto& g = Global::get();
        if (Mod::get()->getSettingValue<bool>("noclip")) {
            g.noclipDead = true;
            g.noclipDeaths++;
            // Make the player semi-transparent to indicate noclip
            int opacity = Mod::get()->getSettingValue<int>("noclip-opacity");
            this->setOpacity(static_cast<GLubyte>(opacity));
            return;
        }
        PlayerObject::playerDestroyed(p0);
    }
};

class $modify(XDBotNoclipPlayLayer, PlayLayer) {

    struct Fields {
        bool respawnScheduled = false;
    };

    void resetLevel() {
        PlayLayer::resetLevel();
        auto& g = Global::get();
        g.noclipDead = false;
        g.leftOver   = 0.f;
        m_fields->respawnScheduled = false;

        // Restore player opacity after noclip death reset
        if (m_player1) m_player1->setOpacity(255);
        if (m_player2) m_player2->setOpacity(255);
    }

    void onDeath(GameObject* obj) {
        PlayLayer::onDeath(obj);

        auto& g = Global::get();
        if (!m_fields->respawnScheduled &&
            Mod::get()->getSettingValue<bool>("auto-respawn")) {
            m_fields->respawnScheduled = true;
            float delay = Mod::get()->getSettingValue<float>("auto-respawn-delay");
            this->scheduleOnce(schedule_selector(XDBotNoclipPlayLayer::doRespawn), delay);
        }
    }

    void doRespawn(float) {
        this->resetLevel();
    }

    void onQuit() {
        auto& g = Global::get();
        if (Mod::get()->getSettingValue<bool>("disable-speedhack-on-exit")) {
            g.speedhackEnabled = false;
            // Reset audio pitch
            if (Mod::get()->getSettingValue<bool>("speedhack-audio")) {
                auto* fmod = FMODAudioEngine::sharedEngine();
                FMOD::ChannelGroup* cg = nullptr;
                if (fmod && fmod->m_system) {
                    fmod->m_system->getMasterChannelGroup(&cg);
                    if (cg) cg->setPitch(1.f);
                }
            }
        }
        PlayLayer::onQuit();
    }
};
