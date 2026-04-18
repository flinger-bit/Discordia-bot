#pragma once
#include <Geode/Geode.hpp>
#include <Geode/ui/ScrollLayer.hpp>
#include "../BotManager.hpp"

using namespace geode::prelude;

// Shows a scrollable list of saved macros with Load / Delete actions.
// Opens from BotLayer's "Load" button.
class MacroListLayer : public Popup<BotLayer*> {
public:
    static MacroListLayer* create(BotLayer* parent);

protected:
    bool setup(BotLayer* parent) override;

private:
    BotLayer* m_parent = nullptr;

    void buildList();
    void onLoad(CCObject*);
    void onDelete(CCObject*);
};
