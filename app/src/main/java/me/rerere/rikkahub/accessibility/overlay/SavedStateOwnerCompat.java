package me.rerere.rikkahub.accessibility.overlay;

import android.view.View;

import androidx.savedstate.SavedStateRegistryOwner;
import androidx.savedstate.ViewTreeSavedStateRegistryOwner;

/** Java bridge for the Android-only savedstate view-tree facade. */
public final class SavedStateOwnerCompat {
    private SavedStateOwnerCompat() {
    }

    public static void set(View view, SavedStateRegistryOwner owner) {
        ViewTreeSavedStateRegistryOwner.set(view, owner);
    }
}
