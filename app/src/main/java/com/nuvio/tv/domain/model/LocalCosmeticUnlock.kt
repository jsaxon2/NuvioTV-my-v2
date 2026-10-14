package com.nuvio.tv.domain.model

/**
 * Fork: the supporter themes (and their matching app icons) are unlocked in this build
 * without a supporter account. Perks the Nuvio servers hold and verify - profile
 * backgrounds and avatars - still need real membership, and the supporter tier (badge,
 * thank-you note) is left untouched.
 */
private val LocallyUnlockedCosmetics = setOf(
    CosmeticEntitlement.GOLD_THEME,
    CosmeticEntitlement.JADE_THEME,
    CosmeticEntitlement.ROSE_GOLD_THEME,
    CosmeticEntitlement.ARCTIC_BLUE_THEME,
    CosmeticEntitlement.GRAPHITE_THEME
)

fun MemberAccess.withLocallyUnlockedCosmetics(): MemberAccess =
    if (entitlements.unlocked.containsAll(LocallyUnlockedCosmetics)) this
    else copy(entitlements = CosmeticEntitlements(entitlements.unlocked + LocallyUnlockedCosmetics))
