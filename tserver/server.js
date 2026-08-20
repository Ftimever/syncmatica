const http = require("http");
const crypto = require("crypto");

const host = process.env.HOST || "127.0.0.1";
const port = Number(process.env.PORT || 8787);

const projectsById = new Map();
const projectIdByIdentity = new Map();
const claimProject = new Map();

function now() {
  return new Date().toISOString();
}

function json(res, status, body) {
  const payload = JSON.stringify(body ?? {});
  res.writeHead(status, {
    "Content-Type": "application/json; charset=utf-8",
    "Content-Length": Buffer.byteLength(payload),
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Headers": "authorization,content-type,x-syncmatica-player",
    "Access-Control-Allow-Methods": "GET,POST,DELETE,OPTIONS"
  });
  res.end(payload);
}

function readBody(req) {
  return new Promise((resolve, reject) => {
    let data = "";
    req.on("data", chunk => {
      data += chunk;
      if (data.length > 80_000_000) {
        reject(new Error("request body too large"));
        req.destroy();
      }
    });
    req.on("end", () => {
      if (!data.trim()) {
        resolve({});
        return;
      }
      try {
        resolve(JSON.parse(data));
      } catch (error) {
        reject(error);
      }
    });
    req.on("error", reject);
  });
}

function stableId(value) {
  return "proj-" + crypto.createHash("sha1").update(value).digest("hex").slice(0, 16);
}

function randomId(prefix) {
  return `${prefix}-${crypto.randomUUID()}`;
}

function authName(req, body) {
  if (body && typeof body.assignee === "string" && body.assignee.trim()) {
    return body.assignee.trim();
  }
  const explicit = req.headers["x-syncmatica-player"];
  if (typeof explicit === "string" && explicit.trim()) {
    return explicit.trim();
  }
  const auth = req.headers.authorization || "";
  if (auth.toLowerCase().startsWith("bearer ") && auth.slice(7).trim()) {
    return auth.slice(7).trim();
  }
  return "local-player";
}

function isBlankOrUnnamed(value) {
  const text = String(value || "").trim();
  return !text || text === "?" || text.toLowerCase() === "unnamed" || text === "未命名";
}

function fileBaseName(value) {
  const text = String(value || "").replace(/\\/g, "/").split("/").pop() || "";
  return text.toLowerCase().endsWith(".litematic") ? text.slice(0, -".litematic".length) : text;
}

function cleanProjectName(placement, serverPlacement, schematicFile, projectId) {
  const candidates = [
    placement && placement.name,
    serverPlacement && serverPlacement.display_name,
    schematicFile && fileBaseName(schematicFile.fileName),
    projectId
  ];
  return String(candidates.find(value => !isBlankOrUnnamed(value)) || projectId);
}

function syncServerPlacementName(project) {
  if (project.serverPlacement && project.project && !isBlankOrUnnamed(project.project.name)) {
    project.serverPlacement.display_name = project.project.name;
  }
}

function identityForImport(body) {
  const placement = body.placement || {};
  const schematicHash = String(placement.schematicHash || "");
  const placementId = String(placement.placementId || "");
  return body.identityMode === "schematic_hash"
    ? `schematic:${schematicHash}`
    : `placement:${schematicHash}:${placementId}`;
}

function normalizeMaterial(material) {
  const required = Number(material.required || 0);
  return {
    materialKey: String(material.materialKey || material.itemId || ""),
    itemId: String(material.itemId || ""),
    nbtHash: String(material.nbtHash || ""),
    displayName: String(material.displayName || material.itemId || material.materialKey || ""),
    required,
    collected: 0,
    reserved: 0,
    missing: Number(material.missing || required)
  };
}

function createProject(body, projectId) {
  const placement = body.placement || {};
  const serverPlacement = body.serverPlacement || null;
  const schematicFile = body.schematicFile || null;
  const timestamp = now();
  const projectName = cleanProjectName(placement, serverPlacement, schematicFile, projectId);
  const project = {
    projectId,
    placementId: String(placement.placementId || ""),
    schematicHash: String(placement.schematicHash || ""),
    name: projectName,
    dimension: String(placement.dimension || ""),
    originX: Number(placement.originX || 0),
    originY: Number(placement.originY || 0),
    originZ: Number(placement.originZ || 0),
    owner: String(placement.owner || ""),
    status: "synced",
    updatedAt: timestamp
  };

  const result = {
    projectId,
    status: "synced",
    updatedAt: timestamp,
    project,
    materials: Array.isArray(body.materials) ? body.materials.map(normalizeMaterial) : [],
    claims: [],
    zones: [],
    collected: {},
    serverPlacement,
    schematicFile
  };
  syncServerPlacementName(result);
  return result;
}

function recompute(project) {
  const reserved = new Map();
  for (const claim of project.claims) {
    reserved.set(claim.materialKey, (reserved.get(claim.materialKey) || 0) + Number(claim.targetAmount || 0));
  }

  for (const material of project.materials) {
    const collected = Object.prototype.hasOwnProperty.call(project.collected, material.materialKey)
      ? Number(project.collected[material.materialKey] || 0)
      : 0;
    const reservedAmount = reserved.get(material.materialKey) || 0;
    material.collected = collected;
    material.reserved = reservedAmount;
    material.missing = Math.max(0, Number(material.required || 0) - collected - reservedAmount);
  }

  const allCollected = project.materials.length > 0 && project.materials.every(material => Number(material.collected || 0) >= Number(material.required || 0));
  const allCovered = project.materials.length > 0 && project.materials.every(material =>
    Number(material.collected || 0) + Number(material.reserved || 0) >= Number(material.required || 0)
  );
  const status = allCollected ? "completed" : (allCovered ? "building" : "collecting");
  project.updatedAt = now();
  project.status = status;
  project.project.status = status;
  project.project.updatedAt = project.updatedAt;
  return project;
}

function publicProject(project) {
  const copy = JSON.parse(JSON.stringify(recompute(project)));
  delete copy.schematicFile;
  return copy;
}

function publicProjectSummary(project) {
  const full = publicProject(project);
  return {
    projectId: full.projectId,
    status: full.status,
    updatedAt: full.updatedAt,
    project: full.project,
    serverPlacement: full.serverPlacement
  };
}

function findProject(id) {
  return projectsById.get(id);
}

async function handle(req, res) {
  const url = new URL(req.url, `http://${req.headers.host || `${host}:${port}`}`);
  const path = url.pathname;

  if (req.method === "OPTIONS") {
    json(res, 204, {});
    return;
  }

  if (req.method === "GET" && path === "/health") {
    json(res, 200, { ok: true, projects: projectsById.size });
    return;
  }

  if (req.method === "POST" && path === "/api/v1/projects/import") {
    const body = await readBody(req);
    const identity = identityForImport(body);
    const projectId = projectIdByIdentity.get(identity) || stableId(identity);
    projectIdByIdentity.set(identity, projectId);

    let project = projectsById.get(projectId);
    if (!project) {
      project = createProject(body, projectId);
      projectsById.set(projectId, project);
    } else {
      const next = createProject(body, projectId);
      project.project = { ...project.project, ...next.project };
      project.materials = next.materials;
      project.serverPlacement = next.serverPlacement || project.serverPlacement;
      project.schematicFile = next.schematicFile || project.schematicFile;
      project.status = "synced";
      project.updatedAt = now();
      syncServerPlacementName(project);
    }

    json(res, 200, publicProject(project));
    return;
  }

  if (req.method === "GET" && path === "/api/v1/projects") {
    json(res, 200, { projects: Array.from(projectsById.values()).map(publicProjectSummary) });
    return;
  }

  const projectMatch = path.match(/^\/api\/v1\/projects\/([^/]+)$/);
  if (req.method === "POST" && projectMatch) {
    const project = findProject(projectMatch[1]);
    if (!project) {
      json(res, 404, { error: "project not found" });
      return;
    }
    const body = await readBody(req);
    if (!isBlankOrUnnamed(body.name)) {
      project.project.name = String(body.name).trim();
      project.updatedAt = now();
      project.project.updatedAt = project.updatedAt;
      syncServerPlacementName(project);
    }
    json(res, 200, publicProject(project));
    return;
  }

  if (req.method === "GET" && projectMatch) {
    const project = findProject(projectMatch[1]);
    if (!project) {
      json(res, 404, { error: "project not found" });
      return;
    }
    json(res, 200, publicProject(project));
    return;
  }

  const schematicMatch = path.match(/^\/api\/v1\/projects\/([^/]+)\/schematic$/);
  if (req.method === "GET" && schematicMatch) {
    const project = findProject(schematicMatch[1]);
    if (!project) {
      json(res, 404, { error: "project not found" });
      return;
    }
    if (!project.schematicFile || !project.schematicFile.data) {
      json(res, 404, { error: "schematic file not found" });
      return;
    }
    json(res, 200, {
      projectId: project.projectId,
      serverPlacement: project.serverPlacement,
      fileName: String(project.schematicFile.fileName || ""),
      hash: String(project.schematicFile.hash || project.project.schematicHash || ""),
      encoding: "base64",
      data: String(project.schematicFile.data || "")
    });
    return;
  }

  const claimsMatch = path.match(/^\/api\/v1\/projects\/([^/]+)\/claims$/);
  if (req.method === "POST" && claimsMatch) {
    const project = findProject(claimsMatch[1]);
    if (!project) {
      json(res, 404, { error: "project not found" });
      return;
    }

    const body = await readBody(req);
    const materialKey = String(body.materialKey || "");
    const assignee = authName(req, body);
    const targetAmount = Math.max(0, Math.floor(Number(body.targetAmount || 0)));
    const existing = project.claims.find(claim =>
      (body.claimId && claim.claimId === body.claimId) ||
      (claim.materialKey === materialKey && claim.assignee.toLowerCase() === assignee.toLowerCase())
    );

    if (targetAmount <= 0) {
      if (existing) {
        project.claims = project.claims.filter(claim => claim.claimId !== existing.claimId);
        claimProject.delete(existing.claimId);
      }
      json(res, 200, publicProject(project));
      return;
    }

    const material = project.materials.find(item => item.materialKey === materialKey);
    if (!material) {
      json(res, 400, { error: "material not found" });
      return;
    }
    const collected = Object.prototype.hasOwnProperty.call(project.collected, materialKey)
      ? Number(project.collected[materialKey] || 0)
      : Number(material.collected || 0);
    const otherReserved = project.claims
      .filter(claim => claim.materialKey === materialKey && (!existing || claim.claimId !== existing.claimId))
      .reduce((sum, claim) => sum + Number(claim.targetAmount || 0), 0);
    const maxClaimable = Math.max(0, Number(material.required || 0) - collected - otherReserved);
    if (targetAmount > maxClaimable) {
      json(res, 409, {
        error: "claim amount exceeds remaining material amount",
        remaining: maxClaimable,
        project: publicProject(project)
      });
      return;
    }

    const claim = existing || {
      claimId: randomId("claim"),
      projectId: project.projectId,
      materialKey,
      assignee,
      fulfilledAmount: 0,
      colorTag: materialKey,
      updatedAt: now()
    };

    claim.materialKey = materialKey;
    claim.assignee = assignee;
    claim.targetAmount = targetAmount;
    claim.colorTag = String(body.colorTag || body.colorMode || materialKey);
    claim.updatedAt = now();

    if (!existing) {
      project.claims.push(claim);
    }
    claimProject.set(claim.claimId, project.projectId);
    json(res, 200, publicProject(project));
    return;
  }

  const deleteClaimMatch = path.match(/^\/api\/v1\/claims\/([^/]+)$/);
  if (req.method === "DELETE" && deleteClaimMatch) {
    const claimId = decodeURIComponent(deleteClaimMatch[1]);
    const projectId = claimProject.get(claimId);
    const project = projectId ? projectsById.get(projectId) : Array.from(projectsById.values()).find(item => item.claims.some(claim => claim.claimId === claimId));
    if (!project) {
      json(res, 404, { error: "claim not found" });
      return;
    }
    project.claims = project.claims.filter(claim => claim.claimId !== claimId);
    claimProject.delete(claimId);
    json(res, 200, publicProject(project));
    return;
  }

  const zonesMatch = path.match(/^\/api\/v1\/projects\/([^/]+)\/zones$/);
  if (req.method === "POST" && zonesMatch) {
    const project = findProject(zonesMatch[1]);
    if (!project) {
      json(res, 404, { error: "project not found" });
      return;
    }
    const body = await readBody(req);
    const zone = {
      zoneId: String(body.zoneId || randomId("zone")),
      projectId: project.projectId,
      dimension: String(body.dimension || project.project.dimension || ""),
      minX: Number(body.minX || 0),
      minY: Number(body.minY || 0),
      minZ: Number(body.minZ || 0),
      maxX: Number(body.maxX || 0),
      maxY: Number(body.maxY || 0),
      maxZ: Number(body.maxZ || 0),
      enabled: body.enabled !== false
    };
    const index = project.zones.findIndex(item => item.zoneId === zone.zoneId);
    if (index >= 0) {
      project.zones[index] = zone;
    } else {
      project.zones.push(zone);
    }
    json(res, 200, publicProject(project));
    return;
  }

  const collectedMatch = path.match(/^\/api\/v1\/projects\/([^/]+)\/collected$/);
  if (req.method === "POST" && collectedMatch) {
    const project = findProject(collectedMatch[1]);
    if (!project) {
      json(res, 404, { error: "project not found" });
      return;
    }
    const body = await readBody(req);
    project.collected = {};
    for (const [key, value] of Object.entries(body)) {
      project.collected[key] = Number(value || 0);
    }
    json(res, 200, publicProject(project));
    return;
  }

  json(res, 404, { error: "not found" });
}

const server = http.createServer((req, res) => {
  handle(req, res).catch(error => {
    json(res, 400, { error: error.message || String(error) });
  });
});

server.listen(port, host, () => {
  console.log(`Syncmatica test third-party server listening on http://${host}:${port}`);
  console.log("Set Syncmatica thirdParty.baseUrl to this URL for local testing.");
});
