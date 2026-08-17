# Syncmatica Configuration Manual

This document explains what the possibilities, options and restrictions of configuring syncmatica are.

## Configuration

syncmatica is configured by a `config.json` which can be found in `config/syncmatica/config.json`. The json inside
configures several settings on the Server/Client which are grouped together into categories. Here is an example of the
current (As of this files last update time current) `config.json`:

### Server config.json

```json
{
	"quota": {
		"enabled": false,
		"limit": 40000000
	},
	"debug": {
		"doPackageLogging": false
	}
}
```

### Client config.json

```json
{
	"thirdParty": {
		"mode": "third_party",
		"legacyServerFallback": false,
		"requireManualShareConfirm": true,
		"baseUrl": "",
		"apiToken": "",
		"requestTimeoutMs": 5000,
		"syncIntervalMs": 30000,
		"uploadSchematicFile": true,
		"projectIdentityMode": "placement_hash",
		"materialKeyMode": "item_nbt",
		"offlineQueueEnabled": true,
		"showProjectListEntry": true,
		"showProjectStatusInPlacementList": true,
		"autoRefreshProjectOnOpen": true,
		"claimsEnabled": true,
		"defaultClaimAmountMode": "smart_stack",
		"allowClaimOverRemaining": false,
		"showOnlyMyClaimsByDefault": false,
		"claimColorMode": "per_material",
		"hudEnabled": true,
		"aggregationMode": "local_only",
		"maxVisibleProjectTags": 2,
		"hoverExpandProjects": true,
		"hideCompleted": true,
		"onlyMine": false,
		"sortMode": "missing_desc",
		"storageZonesEnabled": true,
		"maxZonesPerProject": 8,
		"activationDistance": 32,
		"storageZoneScanIntervalMs": 1500,
		"staleAfterMs": 300000,
		"denyRetryCooldownMs": 30000,
		"countZoneContentsForProject": true,
		"inventoryAggregationEnabled": true,
		"includePlayerInventory": true,
		"includeShulkerContents": true,
		"includeStorageZones": true,
		"includeNonZoneContainers": false,
		"recomputeIntervalMs": 5000,
		"uploadOnlyAggregatedCollected": true,
		"advancedStockingEnabled": false,
		"advancedScanContainers": true,
		"advancedScanIntervalMs": 1000,
		"maxContainersPerCycle": 6,
		"rayLineEnabled": true,
		"lineMaxDistanceTenths": 45,
		"showContainerPreview": true,
		"highlightClaimedItems": true,
		"useClaimColors": true,
		"permissionFailCooldownMs": 60000,
		"nonZoneScansAffectProjectCollected": false,
		"logThirdPartyRequests": false,
		"showScanOverlay": false,
		"logAggregateRecompute": false
	},
	"debug": {
		"doPackageLogging": false
	}
}
```

If the file is missing or otherwise cannot be read, the file might completely reset itself during startup. If parts of
the configuration are damaged/unreadable, the file will try to reset the portions during startup. Extra entries are
ignored.

#### Quota

The key "quota" configures the quota feature of the server.

* `enabled` defines whether the quota feature is enabled on the server. The feature blocks uploads from clients if the
  client exceeds a limit for file uploads. How much the client already uploaded resets itself when the server shuts
  down. Can be `true` or `false`
* `limit` defines the limit that a client is able to upload in bytes.

#### Debug

The key "debug" configures the debug feature of the mod.

* `doPackageLogging` configures whether the client/server should add a debug log for all outgoing and incoming packets.
  The type of the packet and the target of the packet gets logged for outgoing packets - for incoming only the type of
  the packet gets logged.

#### Third Party

The key `thirdParty` configures the client-side third-party project sync mode. The default mode is `third_party`;
`legacy_server` keeps the old server communication path for older servers. The Share button label remains unchanged.

Third-party sync uses HTTP JSON with optional bearer authentication. Project import uses `/api/v1/projects/import`, and
collected material sync uses `/api/v1/projects/{projectId}/collected`. Inventory, shulker, and container details are not
uploaded; only aggregated `materialKey -> collectedAmount` values leave the client.

Local cache files are stored under `config/syncmatica/third_party/` as `projects.json`, `claims.json`,
`scan_cache.json`, and `aggregate_cache.json`.
