import { describe, it, expect } from 'vitest';
import {
  getEncryptionKey,
  encryptToken,
  decryptToken,
  hashServerSecret,
  verifyServerSecret,
  signOAuthState,
  verifyOAuthState,
} from '../src/crypto/encryption.js';

describe('Cryptographic Operations', () => {
  it('should encrypt and decrypt tokens using AES-256-GCM', () => {
    const key = getEncryptionKey();
    const originalToken = 'ya29.a0AXooCgu_secret_refresh_token_from_google_12345';

    const encrypted = encryptToken(originalToken, key);
    expect(encrypted.ciphertext).toBeDefined();
    expect(encrypted.iv).toHaveLength(24); // 12 bytes = 24 hex chars
    expect(encrypted.authTag).toHaveLength(32); // 16 bytes = 32 hex chars

    const decrypted = decryptToken(encrypted.ciphertext, encrypted.iv, encrypted.authTag, key);
    expect(decrypted).toBe(originalToken);
  });

  it('should fail decryption if ciphertext or auth tag is tampered with', () => {
    const key = getEncryptionKey();
    const originalToken = 'tamper_test_token';
    const encrypted = encryptToken(originalToken, key);

    // Tamper with ciphertext
    const tamperedCiphertext = encrypted.ciphertext.slice(0, -2) + '00';
    expect(() => decryptToken(tamperedCiphertext, encrypted.iv, encrypted.authTag, key)).toThrow();

    // Tamper with auth tag
    const tamperedTag = '00' + encrypted.authTag.slice(2);
    expect(() => decryptToken(encrypted.ciphertext, encrypted.iv, tamperedTag, key)).toThrow();
  });

  it('should hash and verify server authentication secrets with timing safety', () => {
    const secret = 'sec_9876543210abcdef0123456789abcdef0123456789abcdef';
    const hash = hashServerSecret(secret);

    expect(verifyServerSecret(secret, hash)).toBe(true);
    expect(verifyServerSecret('wrong_secret_123', hash)).toBe(false);
  });

  it('should sign and verify tamper-proof OAuth state tokens', () => {
    const payload = {
      session_id: '123e4567-e89b-12d3-a456-426614174000',
      nonce: 'random_nonce_value',
      expires_at: Date.now() + 600_000,
    };

    const signedState = signOAuthState(payload);
    expect(signedState).toContain('.');

    const verified = verifyOAuthState<typeof payload>(signedState);
    expect(verified).not.toBeNull();
    expect(verified?.session_id).toBe(payload.session_id);
    expect(verified?.nonce).toBe(payload.nonce);

    // Tampered state
    const tamperedState = signedState.slice(0, -5) + 'xxxxx';
    expect(verifyOAuthState(tamperedState)).toBeNull();
  });
});
