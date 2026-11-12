package com.shiroha.mmdskin;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class NativeFuncJniSmokeTest {
    @Test
    void loadsNativeLibraryAndCallsGetVersion() {
        NativeFunc nativeFunc = NativeFunc.GetInst();
        assertEquals("v1.0.5", nativeFunc.GetVersion());
    }
}
