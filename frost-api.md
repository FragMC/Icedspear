Frost API
=========

Overview
--------

Frost exposes a small Java API so other plugins can:
- control player profiles
- query and grant cosmetics
- purchase cosmetics using the same economy rules as the GUI
- read what a player owns and has equipped
- inspect cosmetic categories and store contents, filtered by a player’s permissions

All API entrypoints live in:
- `com.stufy.fragmc.frost.api.FrostAPI`

Getting the API
---------------

From inside the Frost plugin:

```java
FrostAPI api = getAPI();
```

From another plugin (recommended):

```java
FrostAPI api = (FrostAPI) Bukkit.getServicesManager()
        .load(FrostAPI.class);
```

Make sure you add Frost as a `softdepend` or `depend` in your `plugin.yml` so it is loaded before you use the API.

Profiles
--------

Set a player’s profile:

```java
boolean ok = api.setPlayerProfile(player, "default");
```

- Returns `true` if the profile exists and was applied.
- Also applies all profile-bound items and armor, and clears old profile cosmetics.

Get a player’s current profile:

```java
String profileId = api.getPlayerProfile(player);
```

- Returns the profile ID or `null` if none is set yet.

Check if a profile exists:

```java
boolean exists = api.profileExists("pvp");
```

List all profiles:

```java
Set<String> ids = api.getAvailableProfiles();
```

Cosmetic Ownership
------------------

Check if a player owns a cosmetic:

```java
boolean owns = api.playerOwnsCosmetic(player, "flame_trail");
```

- Respects `frost.admin`:
  - Admins always return `true` here for any cosmetic, even if they do not own it in the database.

Grant a cosmetic (bypass economy):

```java
boolean granted = api.giveCosmetic(player, "flame_trail");
```

- Returns `false` if the cosmetic ID is unknown or player data is not loaded.
- Adds the cosmetic ID to the player’s owned list and saves their data.

Remove a cosmetic:

```java
boolean removed = api.removeCosmetic(player, "flame_trail");
```

- Only removes from the owned list; it does not unequip automatically.

Purchase Cosmetics
------------------

Purchase using Frost’s own rules and Vault economy:

```java
boolean purchased = api.purchaseCosmetic(player, "flame_trail");
```

This method:
- looks up the cosmetic by ID
- checks player data is loaded
- enforces admin rules:
  - admin-only cosmetics require `frost.admin`
  - admins are treated as already owning all cosmetics and cannot buy them
- checks the player does not already own the cosmetic
- checks Vault economy is present
- checks the player’s balance is at least the cosmetic price
- withdraws the price and adds the cosmetic to the owned list if successful
- sends feedback messages directly to the player on success or failure

Use this if you want to sell cosmetics from your own menus but keep all balance, price and admin-only logic consistent with Frost’s GUI.

Owned and Equipped Data
-----------------------

Get IDs of cosmetics a player actually owns:

```java
Set<String> owned = api.getOwnedCosmetics(player);
```

- This returns only what is stored in the database.
- Admins still only show what they explicitly own here, even though they can use everything via permission.

Get equipped cosmetics:

```java
Map<String, String> equipped = api.getEquippedCosmetics(player);
```

Keys follow the same format Frost uses internally:
- particle effects:
  - `particle-effects:ALWAYS`
  - `particle-effects:JUMP`
  - `particle-effects:RIPTIDE`
  - `particle-effects:MACE_SMASH`
  - `particle-effects:SPEAR_CHANGE`
- armor cosmetics:
  - `armor-cosmetics:SET`
  - `armor-cosmetics:HELMET`
  - `armor-cosmetics:CHESTPLATE`
  - `armor-cosmetics:LEGGINGS`
  - `armor-cosmetics:BOOTS`
- other categories:
  - `<category-id>:<slot>`

Values are cosmetic IDs from the config.

Store and Categories
--------------------

Get all cosmetic categories:

```java
Map<String, CosmeticCategory> categories = api.getAllCategories();
```

- Keys are category IDs from `config.yml`, for example:
  - `particle-effects`
  - `armor-cosmetics`
  - `weapon-skins`

Each `CosmeticCategory` exposes:
- category id
- display name
- description
- icon item
- list of `Cosmetic` objects belonging to the category

Get store cosmetics for a player:

```java
List<Cosmetic> store = api.getStoreCosmetics(player);
```

- Returns all cosmetics visible in the store when viewed as this player.
- Respects admin-only flags:
  - cosmetics marked admin-only are hidden unless the player has `frost.admin`.

Get store cosmetics within a single category:

```java
List<Cosmetic> subgroup = api.getStoreCosmeticsInCategory(player, "particle-effects");
```

- Same filtering rules as `getStoreCosmetics`, but only for one category.

Player-Perspective Behaviour
----------------------------

All API methods that depend on permissions behave as if the player used the built-in GUIs:
- admin-only cosmetics are only visible and purchasable for players with `frost.admin`
- admins implicitly have access to all cosmetics even if they do not own them in the database
- owned sets and equipped maps come from the same data Frost uses internally

This makes it safe to build fully custom shops, menus, or NPC interactions around Frost without duplicating permission checks or business logic.

Plugin Instance
---------------

If you need direct access to the Frost plugin:

```java
Frost frost = api.getPlugin();
```

Only use this for advanced integrations. For most cases, prefer the methods above so you stay compatible with future updates.

