# ARM64 only - da chuyen sang A64InlineHook, khong con ho tro ARMv7
APP_ABI := arm64-v8a
# APP_PLATFORM := android-10 #APP_PLATFORM does not need to be set. It will automatically defaulting
APP_STL := c++_static
APP_OPTIM := release
APP_THIN_ARCHIVE := true
APP_PIE 		:= true
