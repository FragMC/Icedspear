/**
 * WebLink Integration for FragMC
 * Integrates Minecraft server control into your website
 */

class WebLinkIntegration {
    constructor(config = {}) {
        // Use config from external config.js if available
        const externalConfig = typeof WEBLINK_CONFIG !== 'undefined' ? WEBLINK_CONFIG : {};

        this.webhookUrl = config.webhookUrl || externalConfig.webhookUrl || 'http://localhost:8080/webhook';
        this.webhookSecret = config.webhookSecret || externalConfig.webhookSecret || '';
        this.debug = config.debug || externalConfig.debug || false;
        this.accid = this.getAccid();
        this.selectedAccount = null;
        this.linkedAccounts = [];

        if (this.debug) {
            console.log('[WebLink] Initialized with URL:', this.webhookUrl);
        }
    }

    /**
     * Get ACCID from cookie
     */
    getAccid() {
        const cookie = document.cookie
            .split('; ')
            .find(row => row.startsWith('accid='));
        return cookie ? cookie.split('=')[1] : null;
    }

    /**
     * Hash ACCID with SHA-256
     */
    async hashAccid(accid) {
        const encoder = new TextEncoder();
        const data = encoder.encode(accid);
        const hashBuffer = await crypto.subtle.digest('SHA-256', data);
        const hashArray = Array.from(new Uint8Array(hashBuffer));
        return hashArray.map(b => b.toString(16).padStart(2, '0')).join('');
    }

    /**
     * Generate nonce for request
     */
    generateNonce() {
        return Array.from(crypto.getRandomValues(new Uint8Array(16)))
            .map(b => b.toString(16).padStart(2, '0'))
            .join('');
    }

    /**
     * Check if user is logged in
     */
    isLoggedIn() {
        return this.accid !== null;
    }

    /**
     * Check if MC account is linked
     */
    async checkAccountLinked() {
        if (!this.isLoggedIn()) {
            return { linked: false, error: 'Not logged in' };
        }

        const hashedAccid = await this.hashAccid(this.accid);

        try {
            if (this.debug) {
                console.log('[WebLink] Checking account link...');
            }

            const response = await fetch(`${this.webhookUrl}/check-link`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({ accid: hashedAccid })
            });

            const data = await response.json();

            if (this.debug) {
                console.log('[WebLink] Link check result:', data);
            }

            if (data.success && data.linked) {
                this.linkedAccounts = [{
                    uuid: data.uuid,
                    username: data.username
                }];
                this.selectedAccount = this.linkedAccounts[0];
            }
            return data;
        } catch (error) {
            console.error('[WebLink] Error checking account link:', error);
            return { linked: false, error: error.message };
        }
    }

    /**
     * Verify link code
     */
    async verifyLinkCode(code) {
        if (!this.isLoggedIn()) {
            throw new Error('Must be logged in to link account');
        }

        const hashedAccid = await this.hashAccid(this.accid);

        try {
            const response = await fetch(`${this.webhookUrl}/verify-code`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({
                    code: code,
                    accid: hashedAccid
                })
            });

            return await response.json();
        } catch (error) {
            console.error('Error verifying code:', error);
            throw error;
        }
    }

    /**
     * Check if player is online
     */
    async checkPlayerOnline() {
        if (!this.selectedAccount) {
            return { online: false, error: 'No account selected' };
        }

        const hashedAccid = await this.hashAccid(this.accid);

        try {
            const response = await fetch(`${this.webhookUrl}/check-online`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({
                    uuid: this.selectedAccount.uuid,
                    accid: hashedAccid
                })
            });

            return await response.json();
        } catch (error) {
            console.error('Error checking online status:', error);
            return { online: false, error: error.message };
        }
    }

    /**
     * Execute command on server
     */
    async executeCommand(command) {
        if (!this.selectedAccount) {
            throw new Error('No account selected');
        }

        const hashedAccid = await this.hashAccid(this.accid);
        const nonce = this.generateNonce();

        try {
            const response = await fetch(`${this.webhookUrl}/execute-command`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({
                    uuid: this.selectedAccount.uuid,
                    accid: hashedAccid,
                    command: command,
                    nonce: nonce
                })
            });

            return await response.json();
        } catch (error) {
            console.error('Error executing command:', error);
            throw error;
        }
    }

    /**
     * Join map (public or private)
     */
    async joinMap(mapId, isPublic = true) {
        const command = isPublic
            ? `map public ${mapId}`
            : `map private ${mapId}`;
        return await this.executeCommand(command);
    }

    /**
     * Leave current map
     */
    async leaveMap() {
        return await this.executeCommand('map leave');
    }

    /**
     * Join map with code
     */
    async joinMapCode(code) {
        return await this.executeCommand(`map join ${code}`);
    }

    /**
     * Create party
     */
    async createParty() {
        return await this.executeCommand('party create');
    }

    /**
     * Join party
     */
    async joinParty(code) {
        return await this.executeCommand(`party join ${code}`);
    }

    /**
     * Leave party
     */
    async leaveParty() {
        return await this.executeCommand('party leave');
    }

    /**
     * Kick from party (host only)
     */
    async kickFromParty(playerName) {
        return await this.executeCommand(`party kick ${playerName}`);
    }

    /**
     * List party members
     */
    async listParty() {
        return await this.executeCommand('party list');
    }

    /**
     * Take party to map (host only)
     */
    async partyToMap(mapId) {
        return await this.executeCommand(`party map ${mapId}`);
    }

    /**
     * Select account from linked accounts
     */
    selectAccount(uuid) {
        const account = this.linkedAccounts.find(acc => acc.uuid === uuid);
        if (account) {
            this.selectedAccount = account;
            return true;
        }
        return false;
    }

    /**
     * Get selected account
     */
    getSelectedAccount() {
        return this.selectedAccount;
    }
}

// Export for use
if (typeof module !== 'undefined' && module.exports) {
    module.exports = WebLinkIntegration;
}