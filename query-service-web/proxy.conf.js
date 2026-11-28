// Dev server proxy (ng serve / npm start): API and query calls go to a QTT on localhost:8080. Sign-in stays off
// here; to work on the sign-in flow, use the UI served by QTT on 8080 (docs/oidc-sso-plan.md).
const target = 'http://localhost:8080';
const PROXY_CONFIG = {
  '/queryrest': { target, secure: false, changeOrigin: true },
  '/query': { target, secure: false, changeOrigin: true },
};

module.exports = PROXY_CONFIG;
