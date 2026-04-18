#pragma once
// Declare the save/load/delete free functions from Global.cpp
#include <string>

bool xdbSaveMacro(const std::string& name);
bool xdbLoadMacro(const std::string& name);
bool xdbDeleteMacro(const std::string& name);
