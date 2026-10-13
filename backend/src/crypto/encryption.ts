import crypto from 'node:crypto';

/**
 * Validates and parses the 32-byte encryption key from environment string.
 */
export function getEncryptionKey(hexOrBase64Key?: string): Buffer {
  const keyStr = hexOrBase64Key || process.env.TOKEN_ENCRYPTION_KEY || '0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef';
  if (keyStr.length === 64) {
    return Buffer.from(keyStr, 'hex');
  }
  const buf = Buffer.from(keyStr, 'base64');
  if (buf.length === 32) {
    return buf;
  }
  // Fallback: derive 32 bytes using SHA-256
  return crypto.createHash('sha256').update(keyStr).digest();
}

/**
 * Encrypts sensitive plaintext (such as Google OAuth refresh tokens) using AES-256-GCM.
 * Generates a unique 12-byte IV for every encryption call.
 */
export function encryptToken(
  plaintext: string,
  key: Buffer = getEncryptionKey()
): { ciphertext: string; iv: string; authTag: string } {
  const iv = crypto.randomBytes(12);
  const cipher = crypto.createCipheriv('aes-256-gcm', key, iv);

  let ciphertext = cipher.update(plaintext, 'utf8', 'hex');
  ciphertext += cipher.final('hex');

  const authTag = cipher.getAuthTag().toString('hex');

  return {
    ciphertext,
    iv: iv.toString('hex'),
    authTag,
  };
}

/**
 * Decrypts AES-256-GCM ciphertext. Verifies authentication tag to detect any tampering.
 */
export function decryptToken(
  ciphertext: string,
  ivHex: string,
  authTagHex: string,
  key: Buffer = getEncryptionKey()
): string {
  const iv = Buffer.from(ivHex, 'hex');
  const authTag = Buffer.from(authTagHex, 'hex');
  const decipher = crypto.createDecipheriv('aes-256-gcm', key, iv);

  decipher.setAuthTag(authTag);

  let plaintext = decipher.update(ciphertext, 'hex', 'utf8');
  plaintext += decipher.final('utf8');

  return plaintext;
}

/**
 * Generates a secure hash for server authentication secrets using SHA-256 with a system salt.
 */
export function hashServerSecret(secret: string): string {
  const salt = process.env.HMAC_STATE_SECRET || 'mcast_salt_default';
  return crypto.createHmac('sha256', salt).update(secret).digest('hex');
}

/**
 * Timing-safe comparison of two hash strings to prevent timing attacks.
 */
export function verifyServerSecret(providedSecret: string, storedHash: string): boolean {
  const computedHash = hashServerSecret(providedSecret);
  const bufA = Buffer.from(computedHash);
  const bufB = Buffer.from(storedHash);
  if (bufA.length !== bufB.length) {
    return false;
  }
  return crypto.timingSafeEqual(bufA, bufB);
}

/**
 * Generates an HMAC-SHA256 signature for OAuth state tokens.
 */
export function signOAuthState(payload: object, secret?: string): string {
  const key = secret || process.env.HMAC_STATE_SECRET || 'mcast_state_secret';
  const data = JSON.stringify(payload);
  const base64Data = Buffer.from(data, 'utf8').toString('base64url');
  const signature = crypto.createHmac('sha256', key).update(base64Data).digest('base64url');
  return `${base64Data}.${signature}`;
}

/**
 * Verifies and decodes an HMAC-SHA256 signed OAuth state token.
 */
export function verifyOAuthState<T = any>(stateString: string, secret?: string): T | null {
  try {
    const key = secret || process.env.HMAC_STATE_SECRET || 'mcast_state_secret';
    const parts = stateString.split('.');
    if (parts.length !== 2) return null;

    const [base64Data, providedSignature] = parts;
    const expectedSignature = crypto.createHmac('sha256', key).update(base64Data).digest('base64url');

    const sigBufA = Buffer.from(providedSignature);
    const sigBufB = Buffer.from(expectedSignature);
    if (sigBufA.length !== sigBufB.length || !crypto.timingSafeEqual(sigBufA, sigBufB)) {
      return null;
    }

    const json = Buffer.from(base64Data, 'base64url').toString('utf8');
    return JSON.parse(json) as T;
  } catch {
    return null;
  }
}
