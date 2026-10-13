import { describe, it, expect } from 'vitest';
import request from 'supertest';
import app from '../src/index.js';

describe('Server Registration & Linking API', () => {
  const serverId = 'srv_test_' + Math.random().toString(36).slice(2, 10);
  const serverSecret = 'sec_super_secret_token_1234567890_abcdefghijklmnopqrstuvwxyz';

  it('POST /v1/servers/register should register a new Paper server installation', async () => {
    const res = await request(app)
      .post('/v1/servers/register')
      .send({
        server_id: serverId,
        server_secret: serverSecret,
        server_name: 'Test Survival World',
        plugin_version: '1.0.0',
        paper_version: 'Paper 1.21.4',
        capabilities: ['nametag', 'chat', 'batch-status'],
      });

    expect(res.status).toBe(200);
    expect(res.body.status).toBe('registered');
    expect(res.body.server_id).toBe(serverId);
    expect(res.body.server_name).toBe('Test Survival World');
  });

  it('POST /v1/servers/heartbeat should update liveness when authenticated', async () => {
    const res = await request(app)
      .post('/v1/servers/heartbeat')
      .set('X-MCast-Server-Id', serverId)
      .set('Authorization', `Bearer ${serverSecret}`)
      .send();

    expect(res.status).toBe(200);
    expect(res.body.acknowledged).toBe(true);
    expect(res.body.server_id).toBe(serverId);
  });

  it('POST /v1/servers/heartbeat should reject unauthenticated requests', async () => {
    const res = await request(app)
      .post('/v1/servers/heartbeat')
      .set('X-MCast-Server-Id', serverId)
      .set('Authorization', 'Bearer invalid_secret')
      .send();

    expect(res.status).toBe(401);
  });

  let createdSessionId = '';
  let createdCode = '';
  const testPlayerUuid = 'a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d';

  it('POST /v1/servers/link-sessions should create a 5-minute single-use session', async () => {
    const res = await request(app)
      .post('/v1/servers/link-sessions')
      .set('X-MCast-Server-Id', serverId)
      .set('Authorization', `Bearer ${serverSecret}`)
      .send({
        player_uuid: testPlayerUuid,
        player_name: 'Notch',
      });

    expect(res.status).toBe(200);
    expect(res.body.session_id).toBeDefined();
    expect(res.body.code).toBeDefined();
    expect(res.body.link_url).toContain('/link/');
    expect(res.body.expires_in_seconds).toBe(300);

    createdSessionId = res.body.session_id;
    createdCode = res.body.code;
  });

  it('GET /v1/servers/link-sessions/:id should report session status', async () => {
    const res = await request(app)
      .get(`/v1/servers/link-sessions/${createdSessionId}`)
      .set('X-MCast-Server-Id', serverId)
      .set('Authorization', `Bearer ${serverSecret}`);

    expect(res.status).toBe(200);
    expect(res.body.status).toBe('pending');
    expect(res.body.player_name).toBe('Notch');
    expect(res.body.code).toBe(createdCode);
  });

  it('GET /v1/link-sessions/verify should verify code publicly for the website', async () => {
    const res = await request(app)
      .get(`/v1/link-sessions/verify?code=${createdCode}`);

    expect(res.status).toBe(200);
    expect(res.body.valid).toBe(true);
    expect(res.body.player_name).toBe('Notch');
    expect(res.body.server_name).toBe('Test Survival World');
    expect(res.body.seconds_remaining).toBeGreaterThan(0);
  });

  it('Cross-server isolation: Server B cannot access Server A link sessions', async () => {
    const otherServerId = 'srv_other_' + Math.random().toString(36).slice(2, 10);
    const otherSecret = 'sec_other_super_secret_token_1234567890_abcdefghijklmnop';

    // Register second server
    await request(app)
      .post('/v1/servers/register')
      .send({
        server_id: otherServerId,
        server_secret: otherSecret,
        server_name: 'Other Server',
      });

    // Try to access server A's session using server B credentials
    const res = await request(app)
      .get(`/v1/servers/link-sessions/${createdSessionId}`)
      .set('X-MCast-Server-Id', otherServerId)
      .set('Authorization', `Bearer ${otherSecret}`);

    expect(res.status).toBe(403);
  });

  it('POST /v1/servers/players/batch-status should query status on-demand for requested players', async () => {
    const res = await request(app)
      .post('/v1/servers/players/batch-status')
      .set('X-MCast-Server-Id', serverId)
      .set('Authorization', `Bearer ${serverSecret}`)
      .send({
        player_uuids: [testPlayerUuid],
      });

    expect(res.status).toBe(200);
    expect(res.body.server_id).toBe(serverId);
    expect(res.body.statuses).toBeDefined();
    expect(res.body.statuses[testPlayerUuid]).toBeDefined();
    expect(res.body.statuses[testPlayerUuid].status).toBe('NOT_LINKED');
  });

  it('Revoked server installation should be blocked from API operations', async () => {
    const revokedServerId = 'srv_revoked_' + Math.random().toString(36).slice(2, 10);
    const revokedSecret = 'sec_revoked_secret_token_1234567890_abcdefghij';

    await request(app)
      .post('/v1/servers/register')
      .send({
        server_id: revokedServerId,
        server_secret: revokedSecret,
        server_name: 'Revoked Test Server',
      });

    // Revoke server directly in db
    const srv = await (await import('../src/db/index.js')).db.getServer(revokedServerId);
    if (srv) {
      srv.is_revoked = true;
    }

    const res = await request(app)
      .post('/v1/servers/heartbeat')
      .set('X-MCast-Server-Id', revokedServerId)
      .set('Authorization', `Bearer ${revokedSecret}`)
      .send();

    expect(res.status).toBe(403);
    expect(res.body.error).toContain('revoked');
  });

  it('POST /v1/servers/players/unlink should remove link', async () => {
    const res = await request(app)
      .post('/v1/servers/players/unlink')
      .set('X-MCast-Server-Id', serverId)
      .set('Authorization', `Bearer ${serverSecret}`)
      .send({
        player_uuid: testPlayerUuid,
      });

    expect(res.status).toBe(200);
    expect(res.body.player_uuid).toBe(testPlayerUuid);
  });
});
