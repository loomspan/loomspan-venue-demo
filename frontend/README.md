# The Annex frontend

React + TypeScript + Vite client for real meeting and workshop assessment and booking. It calls Spring `/api` endpoints; no simulated assessments are used.

Run Spring on port 8080, then `npm ci` and `npm run dev` here. Open the URL printed by Vite (normally http://localhost:5173). Vite proxies `/api` to `http://127.0.0.1:8080`.

`npm run build` checks TypeScript and creates `dist/`. The standard root Maven build installs, builds and copies these assets into the Spring JAR. No profile is required.

The form confirms immutable requirements for the fixed event block. Workshops add two equal breakout groups, standard/vegan lunch counts and optional presentation/livestream. Proposals show exact quote lines, supporting versions and resource reservations. The broader simulated wireframe in `prototype/` is outside this application build.
