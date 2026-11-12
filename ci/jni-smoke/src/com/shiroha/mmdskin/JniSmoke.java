package com.shiroha.mmdskin;

/**
 * Minimal JNI smoke test that does not need Minecraft, Fabric or Gradle.
 * It loads the bundled native library through the real loader and checks that
 * the Rust side reports the version the Java side expects.
 */
public final class JniSmoke {
    private JniSmoke() {
    }

    public static void main(String[] args) {
        String expected = NativeLibraryLoader.LIBRARY_VERSION;
        String actual = NativeFunc.GetInst().GetVersion();
        if (!expected.equals(actual)) {
            System.err.println("JNI smoke test FAILED: expected " + expected + " but Rust reported " + actual);
            System.exit(1);
        }
        System.out.println("JNI smoke test passed: native library reports " + actual);
    }
}
