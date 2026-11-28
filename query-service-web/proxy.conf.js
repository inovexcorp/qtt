// Dev server proxy (ng serve / npm start): API and query calls go to a QTT on localhost:8080, or on QTT_PROXY_TARGET
// (e.g. http://localhost:18082, the plain-HTTP port of scripts/stack.sh). Sign-in stays off here; to work on the
// sign-in flow, use the UI served by QTT on 8080 (docs/oidc-sso-plan.md).
const target = process.env.QTT_PROXY_TARGET || 'http://localhost:8080';
const PROXY_CONFIG = {
  '/queryrest': { target, secure: false, changeOrigin: true },
  '/query': { target, secure: false, changeOrigin: true },
};

module.exports = PROXY_CONFIG;
