# IcedSpear API Documentation

The IcedSpear API allows other plugins to interact with the IcedSpear map and instance system.

## Accessing the API

To access the API, you must first obtain the instance of `IcedSpearAPI`. Ensure that IcedSpear is loaded before your plugin.

```java
import com.stufy.fragmc.icedspear.api.IcedSpearAPI;

IcedSpearAPI api = IcedSpearAPI.getInstance();
```

## Methods

### Map Information

#### `getAvailableMaps`
Get a set of all available map names that are loaded from the configured `map-data-url`.

```java
Set<String> maps = api.getAvailableMaps();
```

#### `getMapData`
Get the raw JSON data associated with a specific map.

```java
JsonObject data = api.getMapData("mapName");
```

### Instance Management

#### `getActiveMapInstances`
Get all currently active map instances.

```java
Map<String, MapInstance> instances = api.getActiveMapInstances();
```

#### `getPlayerMapId`
Get the ID of the map instance that a player is currently in. Returns `null` if the player is not in an IcedSpear map.

```java
String instanceId = api.getPlayerMapId(player.getUniqueId());
```

### Map Creation

#### `createPublicMap`
Create a new public map instance. If a public instance of this map already exists, it returns the existing instance ID.

```java
String instanceId = api.createPublicMap("mapName");
```

#### `createPrivateMap`
Create a new private map instance. This creates a unique instance even if others exist.

```java
String instanceId = api.createPrivateMap("mapName");
```
