#pragma once
#include <Geode/Geode.hpp>
#include <Geode/ui/ScrollLayer.hpp>
#include "../Global.hpp"
#include "../Macro.hpp"

using namespace geode::prelude;

class BotLayer;

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
