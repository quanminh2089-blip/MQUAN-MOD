#include <list>
#include <vector>
#include <string.h>
#include <pthread.h>
#include <cstring>
#include <jni.h>
#include <unistd.h>
#include <fstream>
#include <iostream>
#include <dlfcn.h>
#include "Includes/Logger.h"
#include "Includes/obfuscate.h"
#include "Includes/Utils.h"
#include "KittyMemory/MemoryPatch.h"
#include "Menu.h"
#include "Unity/Unity.h"
#include "Unity/il2cppClass.h"
#include <And64InlineHook/And64InlineHook.hpp>

// ARM64 hook macros - dung A64HookFunction (And64InlineHook)
// NOTE: Substrate da bi xoa. Chi dung ARM64 (arm64-v8a) cho project nay.
#define HOOK(offset, ptr, orig) A64HookFunction((void *)getAbsoluteAddress(targetLibName, string2Offset(OBFUSCATE(offset))), (void *)ptr, (void **)&orig)
#define HOOK_LIB(lib, offset, ptr, orig) A64HookFunction((void *)getAbsoluteAddress(OBFUSCATE(lib), string2Offset(OBFUSCATE(offset))), (void *)ptr, (void **)&orig)

#define HOOK_NO_ORIG(offset, ptr) A64HookFunction((void *)getAbsoluteAddress(targetLibName, string2Offset(OBFUSCATE(offset))), (void *)ptr, NULL)
#define HOOK_LIB_NO_ORIG(lib, offset, ptr) A64HookFunction((void *)getAbsoluteAddress(OBFUSCATE(lib), string2Offset(OBFUSCATE(offset))), (void *)ptr, NULL)

#define HOOKSYM(sym, ptr, org) A64HookFunction(dlsym(dlopen(targetLibName, 4), OBFUSCATE(sym)), (void *)ptr, (void **)&org)
#define HOOKSYM_LIB(lib, sym, ptr, org) A64HookFunction(dlsym(dlopen(OBFUSCATE(lib), 4), OBFUSCATE(sym)), (void *)ptr, (void **)&org)

#define HOOKSYM_NO_ORIG(sym, ptr) A64HookFunction(dlsym(dlopen(targetLibName, 4), OBFUSCATE(sym)), (void *)ptr, NULL)
#define HOOKSYM_LIB_NO_ORIG(lib, sym, ptr) A64HookFunction(dlsym(dlopen(OBFUSCATE(lib), 4), OBFUSCATE(sym)), (void *)ptr, NULL)


//Target lib here
#define targetLibName OBFUSCATE("libil2cpp.so")

#include "Includes/Macros.h"

// fancy struct for patches for kittyMemory
struct My_Patches {

    MemoryPatch SliderExample, Map1, Map2, Map3, Map4, Show, Cam, Showrank, Showname, God,Onehit, Canattack1, Canattack2, Cd30, Autowin, Nocd, Skip55, Fixlag, Cansell, LichSuDau ;
    
} hexPatches;

bool feature1, feature2, featureHookToggle, Health;
bool mod1;
bool mod2;
bool mod3;
bool mod6;
bool ShowSkillState = false;
bool Hackmap = false;
int sliderValue = 1, level = 0;
void *instanceBtn;

// Hooking examples. Assuming you know how to write hook
void (*AddMoneyExample)(void *instance, int amount);


//Show Info

void (*_ShowHeroInfo)(void *instance, int actor, bool bShow);
void ShowHeroInfo(void *instance, int actor, bool bShow) {
    if (instance != NULL&&ShowSkillState) {
      actor = 1;
      bShow = true;
    } 
    _ShowHeroInfo(instance, actor, bShow);
}

void (*_ShowSkillStateInfo)(void *instance, bool bShow);
void ShowSkillStateInfo(void *instance, bool bShow) {
    if (instance != NULL&&ShowSkillState) {
      bShow = true;
    }
    _ShowSkillStateInfo(instance, bShow);
}

void (*_ShowHeroHpInfo)(void *instance, bool bShow);
void ShowHeroHpInfo(void *instance, bool bShow) {
    if (instance != NULL&&ShowSkillState) {
      bShow = true;
    }
    _ShowHeroHpInfo(instance, bShow);
}


void *hack_thread(void *) {
    LOGI(OBFUSCATE("pthread created"));

    //Check if target lib is loaded
    do {
        sleep(1);
    } while (!isLibraryLoaded(targetLibName));

    //Anti-lib rename
    /*
    do {
        sleep(1);
    } while (!isLibraryLoaded("libYOURNAME.so"));*/

    LOGI(OBFUSCATE("%s has been loaded"), (const char *) targetLibName);


// ARM64 opcodes:
//   "20 00 80 D2 C0 03 5F D6" = MOV X0, #1 ; RET  (thay "01 00 A0 E3 1E FF 2F E1" ARM32)
//   "00 00 80 D2 C0 03 5F D6" = MOV X0, #0 ; RET  (thay "00 00 A0 E3 1E FF 2F E1" ARM32)
//   "C0 03 5F D6"             = RET          (thay "1E FF 2F E1" ARM32)
// Cam ARM64: "20 07 80 52 C0 03 5F D6" = MOV W0, #0x81(=129) ; RET
//   (thay "81 01 A0 E3 1E FF 2F E1" ARM32 = MOV R0, #0x81 ; BX LR)
// Antiban NOP ARM64: "1F 20 03 D5 C0 03 5F D6" = NOP ; RET
//   (thay "00 F0 20 E3 1E FF 2F E1" ARM32 = NOP ; BX LR)

hexPatches.Map1 = MemoryPatch::createWithHex(targetLibName,string2Offset(OBFUSCATE("0x23A73CC")),OBFUSCATE("20 00 80 D2 C0 03 5F D6"));
hexPatches.Map2 = MemoryPatch::createWithHex(targetLibName,string2Offset(OBFUSCATE("0x23F14FC")),OBFUSCATE("20 00 80 D2 C0 03 5F D6"));
hexPatches.Map3 = MemoryPatch::createWithHex(targetLibName,string2Offset(OBFUSCATE("0x2585B10")),OBFUSCATE("20 00 80 D2 C0 03 5F D6"));

// Hook ARM64 dung A64HookFunction thay MSHookFunction
A64HookFunction((void *) getAbsoluteAddress("libil2cpp.so", 0x1C09F5C), (void *) ShowHeroInfo, (void **) &_ShowHeroInfo);
A64HookFunction((void *) getAbsoluteAddress("libil2cpp.so", 0x20020C4), (void *) ShowSkillStateInfo, (void **) &_ShowSkillStateInfo);
A64HookFunction((void *) getAbsoluteAddress("libil2cpp.so", 0x20001D4), (void *) ShowHeroHpInfo, (void **) &_ShowHeroHpInfo);

hexPatches.Cam = MemoryPatch::createWithHex(targetLibName,string2Offset(OBFUSCATE("0x1DA8438")),OBFUSCATE("20 07 80 52 C0 03 5F D6"));

//Aimskill

// Antiban - ARM64 NOP + RET
MemoryPatch::createWithHex("libil2cpp.so", 0x1F25F7C, "1F 20 03 D5 C0 03 5F D6").Modify();
MemoryPatch::createWithHex("libil2cpp.so", 0x1F25E48, "1F 20 03 D5 C0 03 5F D6").Modify();

// Show Lich Su Dau
hexPatches.LichSuDau = MemoryPatch::createWithHex(targetLibName,string2Offset(OBFUSCATE("0x16B3DC4")),OBFUSCATE("20 00 80 D2 C0 03 5F D6"));
/*
MemoryPatch::createWithHex("libanogs.so", 0x3AC, "1E FF 2F E1").Modify();
MemoryPatch::createWithHex("libanogs.so", 0x11090, "00 46 70 47").Modify();
MemoryPatch::createWithHex("libanogs.so", 0x124E0, "00 46 70 47").Modify();
MemoryPatch::createWithHex("libanogs.so", 0x1C44C, "1E FF 2F E1").Modify();
MemoryPatch::createWithHex("libanogs.so", 0x1C454, "00 00 00 00").Modify();
MemoryPatch::createWithHex("libanogs.so", 0x1C45C, "1E FF 2F E1").Modify();
MemoryPatch::createWithHex("libanogs.so", 0x1C474, "1E FF 2F E1").Modify();
MemoryPatch::createWithHex("libanogs.so", 0x1E1550, "00 00 70 47").Modify();
MemoryPatch::createWithHex("libanogs.so", 0x1EB150, "70 47 70 47").Modify();
MemoryPatch::createWithHex("libanogs.so", 0x1EB154, "70 47 9A 42").Modify();   
MemoryPatch::createWithHex("libanogs.so", 0x00FC42D8 , "00000000").Modify(); 
MemoryPatch::createWithHex("libanogs.so", 0x00FC4EB4 , "00000000").Modify(); 
MemoryPatch::createWithHex("libanogs.so", 0x00FC4FA8 , "00000000").Modify(); 
MemoryPatch::createWithHex("libanogs.so", 0x00FD3138 , "00000000").Modify(); 
MemoryPatch::createWithHex("libanogs.so", 0x00FD45C4 , "00000000").Modify(); 
MemoryPatch::createWithHex("libanogs.so", 0x01EFBE34 , "00000000").Modify(); 
MemoryPatch::createWithHex("libanogs.so", 0x01EFD378 , "00000000").Modify(); 
MemoryPatch::createWithHex("libil2cpp.so", 0x10D50E8, "00 00 A0 E3 1E FF 2F E1").Modify(); 
MemoryPatch::createWithHex("libil2cpp.so", 0x18BCB9C, "00 00 A0 E3 1E FF 2F E1").Modify();
MemoryPatch::createWithHex("libil2cpp.so", 0x18BCCD0, "00 00 A0 E3 1E FF 2F E1").Modify(); 
MemoryPatch::createWithHex("libil2cpp.so", 0x1EAB148, "00 00 A0 E3 1E FF 2F E1").Modify(); 
MemoryPatch::createWithHex("libil2cpp.so", 0xDEFA00, "00 00 A0 E3 1E FF 2F E1").Modify(); 
MemoryPatch::createWithHex("libil2cpp.so", 0xDF00C0, "00 00 A0 E3 1E FF 2F E1").Modify(); 
MemoryPatch::createWithHex("libil2cpp.so", 0xDF0E48, "00 00 A0 E3 1E FF 2F E1").Modify();
MemoryPatch::createWithHex("libil2cpp.so", 0xDF5028, "00 00 A0 E3 1E FF 2F E1").Modify();
MemoryPatch::createWithHex("libil2cpp.so", 0xDFD228, "00 00 A0 E3 1E FF 2F E1").Modify(); 
MemoryPatch::createWithHex("libil2cpp.so", 0xE01378, "00 00 A0 E3 1E FF 2F E1").Modify();
MemoryPatch::createWithHex("libil2cpp.so", 0xE02968, "00 00 A0 E3 1E FF 2F E1").Modify();
MemoryPatch::createWithHex("libil2cpp.so", 0xE029F8, "00 00 A0 E3 1E FF 2F E1").Modify(); 
MemoryPatch::createWithHex("libil2cpp.so", 0xE0ED58, "00 00 A0 E3 1E FF 2F E1").Modify(); 
MemoryPatch::createWithHex("libil2cpp.so", 0xE0F5C8, "00 00 A0 E3 1E FF 2F E1").Modify(); 
MemoryPatch::createWithHex("libil2cpp.so", 0xE81888, "00 00 A0 E3 1E FF 2F E1").Modify(); 
MemoryPatch::createWithHex("libil2cpp.so", 0x10D29F4, "00 00 A0 E3 1E FF 2F E1").Modify(); 
MemoryPatch::createWithHex("libil2cpp.so", 0x10DF260, "00 00 A0 E3 1E FF 2F E1").Modify();
MemoryPatch::createWithHex("libil2cpp.so", 0x10D50E8, "00 00 A0 E3 1E FF 2F E1").Modify();
*/
   LOGI(OBFUSCATE("Done"));

    return NULL;
}

//JNI calls
extern "C" {

// Do not change or translate the first text unless you know what you are doing
// Assigning feature numbers is optional. Without it, it will automatically count for you, starting from 0
// Assigned feature numbers can be like any numbers 1,3,200,10... instead in order 0,1,2,3,4,5...
// ButtonLink, Category, RichTextView and RichWebView is not counted. They can't have feature number assigned
// Toggle, ButtonOnOff and Checkbox can be switched on by default, if you add True_. Example: CheckBox_True_Check Box
// To learn HTML, go to this page: https://www.w3schools.com/

JNIEXPORT jobjectArray
JNICALL
Java_uk_lgl_modmenu_FloatingModMenuService_getFeatureList(JNIEnv *env, jobject context) {
    jobjectArray ret;

    //Toasts added here so it's harder to remove it
    MakeToast(env, context, OBFUSCATE("MQUAN MOD - AOV ARM64"), Toast::LENGTH_SHORT);

    const char *features[] = {
            // === Game Hack ===
            OBFUSCATE("Collapse_\U0001F5FA️  Game Hack"),
            OBFUSCATE("1_CollapseAdd_ButtonOnOff_\U0001F310  Hack Map"),
            OBFUSCATE("3_CollapseAdd_ButtonOnOff_\U0001F3A5  High Cam"),
            OBFUSCATE("CollapseAdd_RichTextView_"
                "<font color='#FFD54F'>⚠️  Bật Hack Map khi gần vào trận, tắt trước khi trụ vỡ.</font>"),
            // === Show Info ===
            OBFUSCATE("Collapse_\U0001F441️  Show Info"),
            OBFUSCATE("2_CollapseAdd_ButtonOnOff_\U0001F9B8  Show Hero Info"),
            OBFUSCATE("6_CollapseAdd_ButtonOnOff_\U0001F4DC  Show Battle History"),
            // === Link ===
            OBFUSCATE("ButtonLink_\U0001F517  Github & Update_https://github.com/ImTienNguyenZ/CheatAovSoucre"),
    };

    int Total_Feature = (sizeof features / sizeof features[0]);
    ret = (jobjectArray)
            env->NewObjectArray(Total_Feature, env->FindClass(OBFUSCATE("java/lang/String")),
                                env->NewStringUTF(""));

    for (int i = 0; i < Total_Feature; i++)
        env->SetObjectArrayElement(ret, i, env->NewStringUTF(features[i]));

    pthread_t ptid;
    /* Legacy crash watchdog disabled */

    return (ret);
}

JNIEXPORT void JNICALL
Java_uk_lgl_modmenu_Preferences_Changes(JNIEnv *env, jclass clazz, jobject obj,
                                        jint featNum, jstring featName, jint value,
                                        jboolean boolean, jstring str) {

    LOGD(OBFUSCATE("Feature name: %d - %s | Value: = %d | Bool: = %d | Text: = %s"), featNum,
         env->GetStringUTFChars(featName, 0), value,
         boolean, str != NULL ? env->GetStringUTFChars(str, 0) : "");

    //BE CAREFUL NOT TO ACCIDENTLY REMOVE break;

    switch (featNum) {
        case 1:
            mod1 = boolean;
            if (mod1) {
                hexPatches.Map1.Modify();
				hexPatches.Map2.Modify();
                hexPatches.Map3.Modify();
            } else {
                hexPatches.Map1.Restore();
				hexPatches.Map2.Restore();
                hexPatches.Map3.Restore();
            }
            break;
		case 2:
            ShowSkillState = boolean;
            break;
		case 3:
            mod3 = boolean;
            if (mod3) {
                hexPatches.Cam.Modify();
            } else {
                hexPatches.Cam.Restore();
            }
            break;
       case 6:
            mod6 = boolean;
            if (mod6) {
                hexPatches.LichSuDau.Modify();
            } else {
                hexPatches.LichSuDau.Restore();
            }
            break;
    }
}
}

//No need to use JNI_OnLoad, since we don't use JNIEnv
//We do this to hide OnLoad from disassembler
__attribute__((constructor))
void lib_main() {
    // Create a new thread so it does not block the main thread, means the game would not freeze
    pthread_t ptid;
    pthread_create(&ptid, NULL, hack_thread, NULL);
}

/*
JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *globalEnv;
    vm->GetEnv((void **) &globalEnv, JNI_VERSION_1_6);
    return JNI_VERSION_1_6;
}
 */
