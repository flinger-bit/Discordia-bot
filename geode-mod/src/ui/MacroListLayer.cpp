#include "MacroListLayer.hpp"
#include "BotLayer.hpp"

using namespace geode::prelude;

MacroListLayer* MacroListLayer::create(BotLayer* parent) {
    auto* ret = new MacroListLayer();
    if (ret && ret->initAnchored(280.f, 240.f, parent, "GJ_square05.png", "square", {0,0,94,94})) {
        ret->autorelease();
        return ret;
    }
    CC_SAFE_DELETE(ret);
    return nullptr;
}

bool MacroListLayer::setup(BotLayer* parent) {
    m_parent = parent;
    this->setTitle("Load Macro");
    buildList();
    return true;
}

void MacroListLayer::buildList() {
    // Remove previous list
    if (auto* old = m_mainLayer->getChildByTag(88))
        old->removeFromParent();

    auto macros = Global::get().listMacros();

    const float layerW = 240.f, layerH = 155.f, itemH = 34.f;

    auto* scroll = ScrollLayer::create({layerW, layerH});
    scroll->setPosition({(280.f - layerW) / 2.f, 26.f});
    scroll->setTag(88);
    m_mainLayer->addChild(scroll);

    if (macros.empty()) {
        auto* lbl = CCLabelBMFont::create("No macros saved.", "chatFont.fnt");
        lbl->setScale(0.55f);
        lbl->setPosition({layerW / 2.f, layerH / 2.f});
        lbl->setColor({140,140,140});
        scroll->m_contentLayer->addChild(lbl);
        scroll->m_contentLayer->setContentSize({layerW, layerH});
        return;
    }

    float totalH = std::max(layerH, (float)macros.size() * itemH + 10.f);
    scroll->m_contentLayer->setContentSize({layerW, totalH});

    float y = totalH - itemH / 2.f - 5.f;

    for (auto& name : macros) {
        auto* row = CCScale9Sprite::create("square02_small.png", {0,0,40,40});
        row->setContentSize({layerW - 10.f, itemH - 4.f});
        row->setPosition({layerW / 2.f, y});
        row->setColor({15,15,15});
        row->setOpacity(130);
        scroll->m_contentLayer->addChild(row);

        auto* nameLbl = CCLabelBMFont::create(name.c_str(), "chatFont.fnt");
        nameLbl->setScale(0.48f);
        nameLbl->setAnchorPoint({0.f, 0.5f});
        nameLbl->setPosition({8.f, (itemH - 4.f) / 2.f});
        nameLbl->setColor({220,220,220});
        row->addChild(nameLbl);

        auto* menu = CCMenu::create();
        menu->setPosition({0.f, 0.f});
        row->addChild(menu);

        // Load button
        auto* loadBg = CCScale9Sprite::create("square02_small.png", {0,0,40,40});
        loadBg->setContentSize({44.f, 20.f});
        loadBg->setColor({20,100,20});
        auto* loadLbl = CCLabelBMFont::create("Load", "chatFont.fnt");
        loadLbl->setScale(0.4f);
        loadLbl->setPosition({22.f, 10.f});
        loadBg->addChild(loadLbl);
        auto* loadBtn = CCMenuItemSpriteExtra::create(
            loadBg, this, menu_selector(MacroListLayer::onLoad));
        loadBtn->setUserObject(CCString::create(name));
        loadBtn->setPosition({layerW - 70.f, (itemH - 4.f) / 2.f});
        menu->addChild(loadBtn);

        // Delete button
        auto* delBg = CCScale9Sprite::create("square02_small.png", {0,0,40,40});
        delBg->setContentSize({44.f, 20.f});
        delBg->setColor({100,20,20});
        auto* delLbl = CCLabelBMFont::create("Del", "chatFont.fnt");
        delLbl->setScale(0.4f);
        delLbl->setPosition({22.f, 10.f});
        delBg->addChild(delLbl);
        auto* delBtn = CCMenuItemSpriteExtra::create(
            delBg, this, menu_selector(MacroListLayer::onDelete));
        delBtn->setUserObject(CCString::create(name));
        delBtn->setPosition({layerW - 22.f, (itemH - 4.f) / 2.f});
        menu->addChild(delBtn);

        y -= itemH;
    }

    scroll->moveToTop();
}

void MacroListLayer::onLoad(CCObject* sender) {
    auto* btn = dynamic_cast<CCMenuItemSpriteExtra*>(sender);
    if (!btn) return;
    auto* s = dynamic_cast<CCString*>(btn->getUserObject());
    if (!s) return;

    std::string name = s->getCString();
    if (xdbLoadMacro(name)) {
        Notification::create("Loaded: " + name, NotificationIcon::Success)->show();
        this->onClose(nullptr);
    } else {
        FLAlertLayer::create("Error", "Failed to load: " + name, "OK")->show();
    }
}

void MacroListLayer::onDelete(CCObject* sender) {
    auto* btn = dynamic_cast<CCMenuItemSpriteExtra*>(sender);
    if (!btn) return;
    auto* s = dynamic_cast<CCString*>(btn->getUserObject());
    if (!s) return;

    std::string name = s->getCString();
    if (xdbDeleteMacro(name)) {
        Notification::create("Deleted: " + name, NotificationIcon::Info)->show();
        buildList();
    } else {
        FLAlertLayer::create("Error", "Failed to delete: " + name, "OK")->show();
    }
}
