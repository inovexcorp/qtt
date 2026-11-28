/** The sections of the Settings page; the current one is in the URL (?section=…). */
export type SettingsSection = 'overview' | 'sign-in' | 'access' | 'caching' | 'sparqi';

/** The Settings page's sections, in display order (also offered by the quick actions bar). */
export const SETTINGS_SECTIONS: { id: SettingsSection; label: string; icon: string }[] = [
  { id: 'overview', label: 'Overview', icon: 'space_dashboard' },
  { id: 'sign-in', label: 'Sign-in', icon: 'lock' },
  { id: 'access', label: 'Query access', icon: 'vpn_key' },
  { id: 'caching', label: 'Caching', icon: 'cached' },
  { id: 'sparqi', label: 'SPARQi', icon: 'smart_toy' }
];
