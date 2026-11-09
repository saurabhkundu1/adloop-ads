import { createServer } from 'node:http';
import { readFile } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { randomUUID } from 'node:crypto';
import { DatabaseSync } from 'node:sqlite';

const here = dirname(fileURLToPath(import.meta.url));
const database = new DatabaseSync(join(here, 'adloop-dev.sqlite'));
const port = Number(process.env.ADLOOP_PORT || 8787);
const host = process.env.ADLOOP_HOST || '0.0.0.0';
const demoCountry = (process.env.ADLOOP_DEMO_COUNTRY || 'IN').toUpperCase();
const demoCurrency = (process.env.ADLOOP_DEMO_CURRENCY || 'INR').toUpperCase();

if (process.env.NODE_ENV === 'production') {
  throw new Error('This server is a local demo. Do not run it in production.');
}

database.exec(`
  PRAGMA journal_mode = WAL;
  CREATE TABLE IF NOT EXISTS users (
    id TEXT PRIMARY KEY,
    country TEXT NOT NULL,
    currency TEXT NOT NULL,
    created_at INTEGER NOT NULL
  );
  CREATE TABLE IF NOT EXISTS campaigns (
    id TEXT PRIMARY KEY,
    source_type TEXT NOT NULL DEFAULT 'sponsor',
    provider_name TEXT,
    sponsor TEXT NOT NULL,
    headline TEXT NOT NULL,
    description TEXT NOT NULL,
    art_line TEXT NOT NULL,
    mark TEXT NOT NULL,
    top_color TEXT NOT NULL,
    bottom_color TEXT NOT NULL,
    accent_color TEXT NOT NULL,
    countries TEXT NOT NULL,
    duration_seconds INTEGER NOT NULL,
    fee_credits INTEGER NOT NULL,
    viewer_credits INTEGER NOT NULL,
    budget_credits INTEGER NOT NULL,
    spent_credits INTEGER NOT NULL DEFAULT 0,
    active INTEGER NOT NULL DEFAULT 1,
    created_at INTEGER NOT NULL
  );
  CREATE TABLE IF NOT EXISTS view_sessions (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL REFERENCES users(id),
    campaign_id TEXT NOT NULL REFERENCES campaigns(id),
    country TEXT NOT NULL,
    started_at INTEGER NOT NULL,
    completed_at INTEGER,
    status TEXT NOT NULL DEFAULT 'watching'
  );
  CREATE TABLE IF NOT EXISTS ledger (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL REFERENCES users(id),
    campaign_id TEXT NOT NULL REFERENCES campaigns(id),
    view_id TEXT NOT NULL UNIQUE REFERENCES view_sessions(id),
    currency TEXT NOT NULL,
    amount_credits INTEGER NOT NULL,
    created_at INTEGER NOT NULL
  );
`);

const campaignColumns = new Set(database.prepare('PRAGMA table_info(campaigns)').all().map((column) => column.name));
if (!campaignColumns.has('source_type')) database.exec("ALTER TABLE campaigns ADD COLUMN source_type TEXT NOT NULL DEFAULT 'sponsor'");
if (!campaignColumns.has('provider_name')) database.exec('ALTER TABLE campaigns ADD COLUMN provider_name TEXT');

const count = database.prepare('SELECT COUNT(*) AS count FROM campaigns').get().count;
if (count === 0) {
  const seed = database.prepare(`INSERT INTO campaigns
    (id, sponsor, headline, description, art_line, mark, top_color, bottom_color, accent_color,
     countries, duration_seconds, fee_credits, viewer_credits, budget_credits, created_at)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`);
  const now = Date.now();
  [
    ['morrow', 'MORROW COFFEE', 'Make room for slow mornings.', 'Thoughtful coffee for the days that start early and the ones that don\'t.', 'TAKE THE SLOW WAY', 'M', '#435950', '#182E29', '#E4B783'],
    ['noma', 'NOMA SKIN', 'A little ritual, just for you.', 'Simple daily care made with gentle, familiar ingredients.', 'SOFT IS A POWER', 'N', '#9F6A5C', '#4C3032', '#F1C8AD'],
    ['fieldnotes', 'FIELDNOTES AUDIO', 'Find your own frequency.', 'Lightweight sound for long walks, open windows, and everything between.', 'LISTEN YOUR WAY', 'F', '#53627E', '#242C46', '#B7C6ED'],
  ].forEach((ad) => seed.run(...ad, '["*"]', 30, 50, 30, 500, now));
}

const addProviderPreview = database.prepare(`INSERT OR IGNORE INTO campaigns
  (id, source_type, provider_name, sponsor, headline, description, art_line, mark, top_color, bottom_color,
   accent_color, countries, duration_seconds, fee_credits, viewer_credits, budget_credits, created_at)
  VALUES (?, 'provider', ?, ?, ?, ?, ?, ?, ?, ?, ?, '["*"]', ?, ?, ?, ?, ?)`);
[
  ['provider-a-preview', 'DEMO PROVIDER A', 'SAMPLE VIDEO AD', 'A sample provider-supplied placement in the shared reel feed.', 'PROVIDER INVENTORY', 'P', '#53627E', '#242C46', '#B7C6ED', 30, 16, 10, 2000],
  ['provider-b-preview', 'DEMO PROVIDER B', 'ANOTHER SAMPLE AD', 'A second sample provider placement, shown alongside sponsor campaigns.', 'PROVIDER INVENTORY', 'P', '#526C4A', '#263B2A', '#D9F56D', 30, 20, 12, 2000],
].forEach((ad) => addProviderPreview.run(...ad, Date.now()));

const statements = {
  user: database.prepare('SELECT * FROM users WHERE id = ?'),
  createUser: database.prepare('INSERT INTO users (id, country, currency, created_at) VALUES (?, ?, ?, ?)'),
  campaigns: database.prepare(`SELECT c.* FROM campaigns c
    WHERE c.active = 1 AND c.spent_credits + c.fee_credits <= c.budget_credits
      AND (c.countries LIKE ? OR c.countries LIKE ?)
      AND (c.source_type = 'provider' OR NOT EXISTS (
        SELECT 1 FROM ledger l WHERE l.user_id = ? AND l.campaign_id = c.id
      ))
    ORDER BY c.created_at, c.id`),
  campaign: database.prepare('SELECT * FROM campaigns WHERE id = ? AND active = 1'),
  finished: database.prepare('SELECT id FROM ledger WHERE user_id = ? AND campaign_id = ?'),
  activeSession: database.prepare(`SELECT * FROM view_sessions
    WHERE user_id = ? AND status = 'watching' ORDER BY started_at DESC LIMIT 1`),
  createSession: database.prepare(`INSERT INTO view_sessions (id, user_id, campaign_id, country, started_at)
    VALUES (?, ?, ?, ?, ?)`),
  session: database.prepare('SELECT * FROM view_sessions WHERE id = ? AND user_id = ?'),
  completeSession: database.prepare(`UPDATE view_sessions SET status = 'complete', completed_at = ?
    WHERE id = ? AND status = 'watching'`),
  abandonSession: database.prepare(`UPDATE view_sessions SET status = 'abandoned'
    WHERE id = ? AND user_id = ? AND status = 'watching'`),
  spendBudget: database.prepare(`UPDATE campaigns SET spent_credits = spent_credits + ?
    WHERE id = ? AND active = 1 AND spent_credits + ? <= budget_credits`),
  credit: database.prepare(`INSERT INTO ledger (id, user_id, campaign_id, view_id, currency, amount_credits, created_at)
    VALUES (?, ?, ?, ?, ?, ?, ?)`),
  wallet: database.prepare(`SELECT COALESCE(SUM(amount_credits), 0) AS balance
    FROM ledger WHERE user_id = ?`),
  history: database.prepare(`SELECT l.amount_credits, l.currency, l.created_at, c.sponsor, c.source_type, c.provider_name
    FROM ledger l JOIN campaigns c ON c.id = l.campaign_id
    WHERE l.user_id = ? ORDER BY l.created_at DESC LIMIT 50`),
  insertCampaign: database.prepare(`INSERT INTO campaigns
    (id, source_type, sponsor, headline, description, art_line, mark, top_color, bottom_color, accent_color,
     countries, duration_seconds, fee_credits, viewer_credits, budget_credits, created_at)
    VALUES (?, 'sponsor', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`),
};

const localAddresses = new Set(['127.0.0.1', '::1', '::ffff:127.0.0.1', '10.0.2.15']);
function isLocalRequest(request) {
  return localAddresses.has(request.socket.remoteAddress);
}

function send(response, status, value, contentType = 'application/json; charset=utf-8') {
  response.writeHead(status, { 'content-type': contentType, 'cache-control': 'no-store' });
  response.end(contentType.startsWith('application/json') ? JSON.stringify(value) : value);
}

async function body(request) {
  let raw = '';
  for await (const chunk of request) {
    raw += chunk;
    if (raw.length > 16_384) throw new Error('Request too large');
  }
  return raw ? JSON.parse(raw) : {};
}

function countryFor(request) {
  const header = request.headers['cf-ipcountry'];
  if (process.env.ADLOOP_TRUST_CLOUDFLARE === 'true' && /^[A-Z]{2}$/.test(header || '')) return header;
  return demoCountry;
}

function validId(id) {
  return typeof id === 'string' && /^[a-zA-Z0-9_-]{8,80}$/.test(id);
}

function getOrCreateUser(userId, request) {
  if (!validId(userId)) throw Object.assign(new Error('Invalid user id'), { status: 400 });
  const country = countryFor(request);
  const user = statements.user.get(userId);
  if (user && user.country !== country) {
    throw Object.assign(new Error('This account is locked to another country'), { status: 409 });
  }
  if (!user) statements.createUser.run(userId, country, demoCurrency, Date.now());
  return statements.user.get(userId);
}

function campaignJson(row) {
  return {
    id: row.id,
    source: row.source_type,
    sourceLabel: row.source_type === 'provider' ? row.provider_name : 'DIRECT SPONSOR',
    brand: row.sponsor,
    headline: row.headline,
    description: row.description,
    artLine: row.art_line,
    mark: row.mark,
    topColor: row.top_color,
    bottomColor: row.bottom_color,
    accentColor: row.accent_color,
    durationSeconds: row.duration_seconds,
    feeCredits: row.fee_credits,
    rewardCredits: row.viewer_credits,
    currency: demoCurrency,
  };
}

const server = createServer(async (request, response) => {
  if (!isLocalRequest(request)) return send(response, 403, { error: 'Local demo server only' });
  const url = new URL(request.url, `http://${request.headers.host || 'localhost'}`);
  try {
    if (request.method === 'GET' && url.pathname === '/health') return send(response, 200, { ok: true });
    if (request.method === 'GET' && url.pathname === '/sponsor') {
      return send(response, 200, await readFile(join(here, 'sponsor.html'), 'utf8'), 'text/html; charset=utf-8');
    }
    if (request.method === 'POST' && url.pathname === '/api/users') {
      const input = await body(request);
      const user = getOrCreateUser(input.userId, request);
      return send(response, 200, { userId: user.id, country: user.country, currency: user.currency });
    }
    if (request.method === 'GET' && url.pathname === '/api/feed') {
      const user = getOrCreateUser(url.searchParams.get('userId'), request);
      const rows = statements.campaigns.all(`%"${user.country}"%`, '%"*"%', user.id);
      return send(response, 200, { country: user.country, currency: user.currency, campaigns: rows.map(campaignJson) });
    }
    if (request.method === 'POST' && url.pathname === '/api/views/start') {
      const input = await body(request);
      const user = getOrCreateUser(input.userId, request);
      const campaign = statements.campaign.get(input.campaignId);
      if (!campaign || !JSON.parse(campaign.countries).some((code) => code === '*' || code === user.country)) {
        return send(response, 404, { error: 'Campaign unavailable in this country' });
      }
      if (campaign.source_type === 'sponsor' && statements.finished.get(user.id, campaign.id)) {
        return send(response, 409, { error: 'This sponsor campaign was already completed' });
      }
      const current = statements.activeSession.get(user.id);
      if (current) statements.abandonSession.run(current.id, user.id);
      const sessionId = randomUUID();
      const startedAt = Date.now();
      statements.createSession.run(sessionId, user.id, campaign.id, user.country, startedAt);
      return send(response, 201, { sessionId, startedAt, durationSeconds: campaign.duration_seconds });
    }
    if (request.method === 'POST' && url.pathname === '/api/views/complete') {
      const input = await body(request);
      const user = getOrCreateUser(input.userId, request);
      const session = statements.session.get(input.sessionId, user.id);
      if (!session) return send(response, 404, { error: 'Watch session not found' });
      const campaign = statements.campaign.get(session.campaign_id);
      if (session.status === 'complete') {
        const previous = database.prepare('SELECT amount_credits FROM ledger WHERE view_id = ?').get(session.id);
        return send(response, 200, { credited: previous?.amount_credits || 0, duplicate: true });
      }
      if (session.status !== 'watching' || !campaign) return send(response, 409, { error: 'Watch session is no longer active' });
      const elapsed = (Date.now() - session.started_at) / 1000;
      if (elapsed < campaign.duration_seconds) return send(response, 409, { error: 'Watch the full ad before claiming' });

      database.exec('BEGIN IMMEDIATE');
      try {
        const spend = statements.spendBudget.run(campaign.fee_credits, campaign.id, campaign.fee_credits);
        if (spend.changes !== 1) throw Object.assign(new Error('Sponsor campaign budget is exhausted'), { status: 409 });
        statements.completeSession.run(Date.now(), session.id);
        statements.credit.run(randomUUID(), user.id, campaign.id, session.id, user.currency, campaign.viewer_credits, Date.now());
        database.exec('COMMIT');
      } catch (error) {
        database.exec('ROLLBACK');
        throw error;
      }
      return send(response, 200, { credited: campaign.viewer_credits, currency: user.currency, duplicate: false });
    }
    if (request.method === 'POST' && url.pathname === '/api/views/cancel') {
      const input = await body(request);
      const user = getOrCreateUser(input.userId, request);
      statements.abandonSession.run(input.sessionId, user.id);
      return send(response, 200, { ok: true });
    }
    if (request.method === 'GET' && url.pathname === '/api/wallet') {
      const user = getOrCreateUser(url.searchParams.get('userId'), request);
      return send(response, 200, {
        country: user.country,
        currency: user.currency,
        balance: statements.wallet.get(user.id).balance,
        history: statements.history.all(user.id),
      });
    }
    if (request.method === 'POST' && url.pathname === '/api/sponsor/campaigns') {
      const input = await body(request);
      const textFields = ['sponsor', 'headline', 'description'];
      if (textFields.some((field) => typeof input[field] !== 'string' || !input[field].trim() || input[field].length > 160)) {
        return send(response, 400, { error: 'Sponsor, headline, and description are required (160 characters max)' });
      }
      const duration = Number(input.durationSeconds);
      const fee = Number(input.feeCredits);
      const reward = Number(input.rewardCredits);
      const budget = Number(input.budgetCredits);
      if (!Number.isInteger(duration) || duration < 5 || duration > 120 ||
          !Number.isSafeInteger(fee) || !Number.isSafeInteger(reward) || !Number.isSafeInteger(budget) ||
          reward < 1 || fee < reward || budget < fee) {
        return send(response, 400, { error: 'Use a 5–120 second ad, positive whole credits, reward ≤ fee, and budget ≥ fee' });
      }
      const countries = String(input.countries || '*').split(',').map((code) => code.trim().toUpperCase());
      if (!countries.length || countries.some((code) => code !== '*' && !/^[A-Z]{2}$/.test(code))) {
        return send(response, 400, { error: 'Countries must be * or comma-separated ISO country codes' });
      }
      const colors = ['topColor', 'bottomColor', 'accentColor'].map((key) => input[key] || '#435950');
      if (colors.some((color) => !/^#[0-9a-fA-F]{6}$/.test(color))) return send(response, 400, { error: 'Colors must be six-digit hex values' });
      const id = randomUUID().replaceAll('-', '');
      statements.insertCampaign.run(id, input.sponsor.trim(), input.headline.trim(), input.description.trim(),
        'SPONSORED CAMPAIGN', input.sponsor.trim()[0].toUpperCase(), ...colors, JSON.stringify(countries),
        duration, fee, reward, budget, Date.now());
      return send(response, 201, { id, status: 'demo-active', message: 'Demo campaign created; no payment was collected.' });
    }
    return send(response, 404, { error: 'Not found' });
  } catch (error) {
    return send(response, error.status || 400, { error: error.message || 'Invalid request' });
  }
});

server.listen(port, host, () => {
  console.log(`AdLoop local demo API: http://${host}:${port}`);
  console.log(`Demo region: ${demoCountry} · ${demoCurrency}; sponsor console: /sponsor`);
});
