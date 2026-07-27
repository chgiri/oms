/**
 * Shared kernel, not a business module — base entity/DTO types, cross-cutting
 * config (caching, rate limiting, distributed locks), and the central
 * GlobalExceptionHandler that every other module's exceptions flow through.
 * It has no repository or module-private storage of its own, so unlike the
 * business modules it's marked fully OPEN: every subpackage is part of its
 * public surface, and — unlike a NamedInterface-by-NamedInterface module —
 * common itself is also permitted to reach into other modules' exception
 * packages (see GlobalExceptionHandler), which is the one legitimate reason
 * a "shared" type needs to see every module's public error types in one place.
 */
@org.springframework.modulith.ApplicationModule(type = org.springframework.modulith.ApplicationModule.Type.OPEN)
package com.giri.oms.common;
