# The Annex frontend

Real React + TypeScript + Vite client for the room-only slice. It calls Spring `/api` endpoints; no fixture responses or simulated assessments are used.

Run Spring on port 8080, then `npm ci` and `npm run dev` here. Vite proxies `/api` to `http://127.0.0.1:8080`. Update `vite.config.ts` if you change the backend port.

`npm run build` checks TypeScript and creates `dist/`. The root Maven `with-frontend` profile installs, builds and copies these assets into the Spring application JAR.

The current form intentionally supports one room and a fixed event block. The broader hosted wireframe remains in `prototype/`, outside this application build.
