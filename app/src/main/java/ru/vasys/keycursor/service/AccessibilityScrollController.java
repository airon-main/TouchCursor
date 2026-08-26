package ru.vasys.keycursor.service;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Rect;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.List;

/**
 * Performs real Accessibility scroll actions on the scrollable node underneath the cursor.
 * It only inspects node bounds and supported action IDs; it never reads node text/content.
 */
final class AccessibilityScrollController {
    private final AccessibilityService service;
    private final Rect bounds = new Rect();

    AccessibilityScrollController(AccessibilityService service) {
        this.service = service;
    }

    boolean scrollAt(float x, float y, float dx, float dy) {
        int directionalAction = directionalAction(dx, dy);
        if (directionalAction == 0) return false;

        AccessibilityNodeInfo root = service.getRootInActiveWindow();
        if (root == null) return false;

        AccessibilityNodeInfo target = null;
        try {
            target = findDeepestSupportingNode(root, Math.round(x), Math.round(y), directionalAction);
            if (target != null && target.performAction(directionalAction)) return true;

            int fallbackAction = fallbackAction(dx, dy);
            if (fallbackAction == 0) return false;

            if (target != null) {
                target.recycle();
                target = null;
            }
            target = findDeepestSupportingNode(root, Math.round(x), Math.round(y), fallbackAction);
            return target != null && target.performAction(fallbackAction);
        } finally {
            if (target != null) target.recycle();
            root.recycle();
        }
    }

    private AccessibilityNodeInfo findDeepestSupportingNode(
            AccessibilityNodeInfo node, int x, int y, int actionId) {
        node.getBoundsInScreen(bounds);
        if (!bounds.contains(x, y)) return null;

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;
            AccessibilityNodeInfo match;
            try {
                match = findDeepestSupportingNode(child, x, y, actionId);
            } finally {
                child.recycle();
            }
            if (match != null) return match;
        }

        return supportsAction(node, actionId) ? AccessibilityNodeInfo.obtain(node) : null;
    }

    private boolean supportsAction(AccessibilityNodeInfo node, int actionId) {
        List<AccessibilityNodeInfo.AccessibilityAction> actions = node.getActionList();
        for (AccessibilityNodeInfo.AccessibilityAction action : actions) {
            if (action.getId() == actionId) return true;
        }
        return false;
    }

    private int directionalAction(float dx, float dy) {
        if (Math.abs(dy) >= Math.abs(dx)) {
            if (dy < 0f) return AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.getId();
            if (dy > 0f) return AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.getId();
        } else {
            if (dx < 0f) return AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_LEFT.getId();
            if (dx > 0f) return AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_RIGHT.getId();
        }
        return 0;
    }

    private int fallbackAction(float dx, float dy) {
        if (Math.abs(dy) >= Math.abs(dx)) {
            if (dy < 0f) return AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD;
            if (dy > 0f) return AccessibilityNodeInfo.ACTION_SCROLL_FORWARD;
        } else {
            if (dx < 0f) return AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD;
            if (dx > 0f) return AccessibilityNodeInfo.ACTION_SCROLL_FORWARD;
        }
        return 0;
    }
}
