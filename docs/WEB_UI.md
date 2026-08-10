# Web UI

## Tech Stack

React + TypeScript, Ant Design components, Vite build tool.

- Source: `src/main/webapp/`
- Build output: `src/main/resources/webapp/`

## Workflow

```bash
cd src/main/webapp
npm run dev      # development with hot-reload (backend proxies /api)
npm run build    # production build — always run after changes
npm run lint
```

After any change, rebuild (`npm run build`) and verify in the running application before committing.

## API Integration

- v2 API: `/api/v2/` — legacy: `/api/`
- Use the axios instance from `src/services/api.ts`; it injects the API key automatically
- Handle loading states and errors; show user-friendly messages

## Code Style

- Functional components with hooks
- Named exports; avoid `any` — use explicit TypeScript types
- All Ant Design components must be imported explicitly
- Keep API calls in `src/services/api.ts`
