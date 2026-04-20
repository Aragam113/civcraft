package com.civcraft.faction;

import java.util.UUID;

public record Faction(UUID ownerId, String name, int color) {
}
