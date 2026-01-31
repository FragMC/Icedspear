/**
 * Website Configuration Template
 *
 * INSTRUCTIONS:
 * 1. Copy this file to config.js
 * 2. Fill in your actual values
 * 3. NEVER commit config.js to Git (it's in .gitignore)
 * 4. Import this in your HTML: <script src="config.js"></script>
 */

const WEBLINK_CONFIG = {
    // ============================================
    // WEBHOOK ENDPOINT
    // ============================================

    // Your webhook server URL
    // DEVELOPMENT: Use 'http://localhost:8080/webhook'
    // PRODUCTION: Use 'https://api.yourserver.com/webhook'
    webhookUrl: 'http://141.147.118.157:25531/webhook',

    // ============================================
    // SECURITY (OPTIONAL)
    // ============================================

    // Webhook secret for HMAC signing (optional but recommended)
    // This should match the webhook-secret in your server config.yml
    // Leave empty ('') if not using signature verification
    webhookSecret: '',

    // ============================================
    // UI CONFIGURATION
    // ============================================

    // Available maps for quick join
    // Add/remove maps as needed
    availableMaps: [
        { id: 'skyblock', name: 'Skyblock', description: 'Classic skyblock survival' },
        { id: 'pvp_arena', name: 'PVP Arena', description: 'Fast-paced combat' },
        { id: 'survival', name: 'Survival', description: 'Standard survival mode' },
        { id: 'creative', name: 'Creative', description: 'Build mode' }
    ],

    // Enable debug logging to console
    debug: false,

    // Show detailed error messages to users (disable in production)
    showDetailedErrors: true,

    // Auto-refresh online status interval (milliseconds)
    // Set to 0 to disable auto-refresh
    statusRefreshInterval: 30000, // 30 seconds

    // ============================================
    // FEATURE FLAGS
    // ============================================

    features: {
        // Enable map controls
        enableMapControl: true,

        // Enable party controls
        enablePartyControl: true,

        // Enable friends system (if implemented)
        enableFriends: false,

        // Show player statistics (if implemented)
        enableStats: false
    }
};

// Export for use in modules
if (typeof module !== 'undefined' && module.exports) {
    module.exports = WEBLINK_CONFIG;
}