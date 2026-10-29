"""Verify SQL v3 on an EMPTY disposable MySQL server, never the application DB.

Example: python Docs/check_speakmate_schema.py --mysql "C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe" --port 13317
Creates test-only speakmate data; the caller owns starting/stopping the disposable server.
Only standard-library modules; no application or AI tests are claimed.
"""
import argparse
import json
from pathlib import Path
import subprocess

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--mysql', required=True)
parser.add_argument('--port', type=int, required=True)
args = parser.parse_args()
if args.port == 3306:
    raise SystemExit('Refusing the default application MySQL port; use a disposable server.')
command = [args.mysql, '--no-defaults', '--protocol=TCP', '--host=127.0.0.1',
           f'--port={args.port}', '--user=root', '--default-character-set=utf8mb4',
           '--batch', '--raw', '--skip-column-names']
passed = []
boundaries = []


def run(sql, database=True):
    return subprocess.run(command + (['speakmate'] if database else []), input=sql,
                          encoding='utf-8', capture_output=True)


def success(label, sql, expected=None):
    result = run(sql)
    if result.returncode or (expected is not None and result.stdout.strip() != expected):
        raise AssertionError(f'{label}: {result.stderr.strip()} / output={result.stdout.strip()!r}')
    passed.append(label)
    return result.stdout.strip()


def rejected(label, sql, error=3819):
    result = run('START TRANSACTION;\n' + sql + '\nROLLBACK;')
    if result.returncode == 0 or f'ERROR {error} ' not in result.stderr:
        raise AssertionError(f'{label}: expected MySQL {error}, got {result.stderr.strip()}')
    passed.append(label)


def boundary(label, sql, expected='1'):
    result = run('START TRANSACTION;\n' + sql + '\nROLLBACK;')
    if result.returncode or result.stdout.strip() != expected:
        raise AssertionError(f'Boundary changed: {label}: {result.stderr} {result.stdout!r}')
    boundaries.append(label)


fresh = run("SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name NOT IN "
            "('mysql','sys','performance_schema','information_schema');", database=False)
if fresh.returncode or fresh.stdout.strip() != '0':
    raise SystemExit('Refusing nonempty/unreachable server; use an isolated fresh MySQL instance.')
schema = Path(__file__).resolve().parents[1] / 'speakmate.sql'
imported = run(schema.read_text(encoding='utf-8-sig'), database=False)
if imported.returncode:
    raise SystemExit(f'Schema import failed: {imported.stderr}')
passed.append('import SQL on fresh server')
version = success('MySQL version', 'SELECT VERSION();')
success('seven tables', "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='speakmate';", '7')
success('eight foreign keys', "SELECT COUNT(*) FROM information_schema.referential_constraints WHERE constraint_schema='speakmate';", '8')
success('seed test accounts and topic', """
INSERT INTO users(id,name,email,password) VALUES(1,'An','an@example.test','TEST_HASH_NOT_A_CREDENTIAL'),(2,'Binh','binh@example.test','TEST_HASH_NOT_A_CREDENTIAL');
INSERT INTO users(id,name,email,password,role) VALUES(3,'Admin','admin@example.test','TEST_HASH_NOT_A_CREDENTIAL','ADMIN');
INSERT INTO topics(id,name,scenario) VALUES(1,'Hotel check-in','You are a hotel receptionist.');
""")
success('default role and level', 'SELECT role,level FROM users WHERE id=1;', 'USER\tA1')
success('admin same users table', "SELECT COUNT(*) FROM users WHERE role='ADMIN';", '1')
success('Google-only account', "INSERT INTO users(id,name,email,google_subject) VALUES(4,'Google','google@example.test','google-subject-4');")
for label, sql, error in [
    ('account requires credential', "UPDATE users SET password=NULL WHERE id=1;", 3819),
    ('unique Google subject', "UPDATE users SET google_subject='google-subject-4' WHERE id=1;", 1062),
    ('blank Google subject', "UPDATE users SET google_subject=' ' WHERE id=4;", 3819),
    ('refresh requires expiry', "UPDATE users SET refresh_token=REPEAT('a',64) WHERE id=1;", 3819),
    ('expiry requires refresh', "UPDATE users SET refresh_expires_at=NOW(6) WHERE id=1;", 3819),
    ('refresh hash length', "UPDATE users SET refresh_token='raw-token',refresh_expires_at=NOW(6) WHERE id=1;", 3819),
    ('negative auth version', 'UPDATE users SET auth_version=-1 WHERE id=1;', 3819),
    ('reset attempts bounded', 'UPDATE users SET password_reset_attempts=6 WHERE id=1;', 3819),
    ('reset requires expiry', "UPDATE users SET password_reset_code_hash='TEST_HASH' WHERE id=1;", 3819),
    ('reset requires sent time', "UPDATE users SET password_reset_code_hash='TEST_HASH',password_reset_expires_at=NOW(6) WHERE id=1;", 3819),
]:
    rejected(label, sql, error)
for label, sql, error in [
    ('duplicate email case insensitive', "UPDATE users SET email='AN@example.test' WHERE id=2;", 1062),
    ('lowercase role', "UPDATE users SET role='admin' WHERE id=1;", 3819),
    ('role trailing space', "UPDATE users SET role='USER ' WHERE id=1;", 3819),
    ('invalid level', "UPDATE users SET level='C1' WHERE id=1;", 3819),
    ('boolean 2', 'UPDATE users SET is_active=2 WHERE id=1;', 3819),
    ('blank scenario', "UPDATE topics SET scenario='  ' WHERE id=1;", 3819),
]:
    rejected(label, sql, error)
success('open conversations A1 profile B2 choice', """
INSERT INTO conversations(id,user_id,topic_id,create_request_id,profile_level_snapshot,level,topic_name_snapshot,scenario_snapshot)
VALUES(1,1,1,'00000000-0000-4000-8000-000000000001','A1','B2','Hotel check-in','Original scenario'),
(2,2,1,'00000000-0000-4000-8000-000000000002','A2','A2','Hotel check-in','Original scenario');
""")
rejected('missing topic FK', 'UPDATE conversations SET topic_id=999 WHERE id=1;', 1452)
rejected('duplicate create request', "UPDATE conversations SET user_id=1,create_request_id='00000000-0000-4000-8000-000000000001' WHERE id=2;", 1062)
for label, assignment in [
    ('close without review state', 'ended_at=NOW(6)'),
    ('PROCESSING without close', "review_status='PROCESSING'"),
    ('FAILED without close', "review_status='FAILED'"),
    ('token missing timestamp', "processing_token='00000000-0000-4000-8000-000000000009'"),
    ('timestamp missing token', 'processing_started_at=NOW(6)'),
    ('report while open', "summary='Premature review'"),
    ('rating while open', 'self_rating=4'),
    ('closed PROCESSING without token', "ended_at=NOW(6),review_status='PROCESSING'"),
    ('COMPLETED missing report', "ended_at=NOW(6),review_status='COMPLETED'"),
    ('INSUFFICIENT missing reason', "ended_at=NOW(6),review_status='INSUFFICIENT_DATA'"),
]:
    rejected(label, f'UPDATE conversations SET {assignment} WHERE id=1;')
success('receive reply task', "UPDATE conversations SET processing_token='00000000-0000-4000-8000-000000000009',processing_started_at=NOW(6) WHERE id=1;")
success('paired messages and other conversation message', """
INSERT INTO messages(id,conversation_id,sender,content,request_id) VALUES
(1,1,'USER','I have a reservation.','00000000-0000-4000-8000-000000000011'),
(2,1,'AI','What name is the booking under?','00000000-0000-4000-8000-000000000011'),
(3,2,'USER','Another learner speaks.','00000000-0000-4000-8000-000000000012');
UPDATE conversations SET processing_token=NULL,processing_started_at=NULL WHERE id=1;
""")
rejected('duplicate request sender', "INSERT INTO messages(conversation_id,sender,content,request_id) VALUES(1,'USER','Duplicate','00000000-0000-4000-8000-000000000011');", 1062)
for label, assignment, record in [
    ('AI correction forbidden', "corrected_content='Corrected',feedback='Reason'", 2),
    ('correction needs feedback', "corrected_content='Corrected'", 1),
    ('feedback needs correction', "feedback='Reason'", 1),
    ('blank correction forbidden', "corrected_content=' ',feedback='Reason'", 1),
    ('blank message', "content='  '", 1),
    ('USER length bound', "content=REPEAT('x',1001)", 1),
]:
    rejected(label, f'UPDATE messages SET {assignment} WHERE id={record};')
boundary('Service must prohibit corrections before finish', "UPDATE messages SET corrected_content='I have a reservation.',feedback='Test only' WHERE id=1; SELECT COUNT(*) FROM messages WHERE corrected_content IS NOT NULL;")
boundary('Service must prohibit suggestions before finish', "INSERT INTO word_suggestions(conversation_id,word,meaning) VALUES(1,'early','test'); SELECT COUNT(*) FROM word_suggestions;")
success('admin edits and disables topic', "UPDATE topics SET name='Changed topic',scenario='Changed scenario',is_active=0 WHERE id=1;")
success('old snapshot preserved', 'SELECT topic_name_snapshot,scenario_snapshot FROM conversations WHERE id=1;', 'Hotel check-in\tOriginal scenario')
rejected('used topic cannot be deleted', 'DELETE FROM topics WHERE id=1;', 1451)
boundary('Service must reject starting disabled topic', "INSERT INTO conversations(user_id,topic_id,create_request_id,profile_level_snapshot,level,topic_name_snapshot,scenario_snapshot) VALUES(1,1,'00000000-0000-4000-8000-000000000077','A1','A1','Disabled','Disabled'); SELECT COUNT(*) FROM conversations WHERE create_request_id='00000000-0000-4000-8000-000000000077';")
success('close with review task', "UPDATE conversations SET ended_at=NOW(6),review_status='PROCESSING',processing_token='00000000-0000-4000-8000-000000000020',processing_started_at=NOW(6) WHERE id=1;")
ended = success('capture ended time', 'SELECT ended_at FROM conversations WHERE id=1;')
rejected('FAILED cannot retain token', "UPDATE conversations SET review_status='FAILED' WHERE id=1;")
success('review failure preserves transcript', "UPDATE conversations SET review_status='FAILED',processing_token=NULL,processing_started_at=NULL WHERE id=1; SELECT COUNT(*) FROM messages WHERE conversation_id=1;", '2')
success('retry review', "UPDATE conversations SET review_status='PROCESSING',processing_token='00000000-0000-4000-8000-000000000021',processing_started_at=NOW(6) WHERE id=1;")
report = "review_status='COMPLETED',processing_token=NULL,processing_started_at=NULL,summary='Test summary',strengths='Test strength',weaknesses='Test improvement',recommended_level='A1',level_feedback='Test reason'"
rejected('COMPLETED nullable recommendation is required', f'UPDATE conversations SET {report},recommended_level=NULL WHERE id=1;')
rejected('COMPLETED blank summary', f"UPDATE conversations SET {report},summary='  ' WHERE id=1;")
success('atomic report and suggestions', f"""
START TRANSACTION;
UPDATE conversations SET {report} WHERE id=1;
UPDATE messages SET corrected_content='I have a reservation.',feedback='Test explanation' WHERE id=1;
INSERT INTO word_suggestions(id,conversation_id,word,meaning,example) VALUES(1,1,'reservation','dat cho','I have a reservation.'),(2,1,'breakfast','bua sang',NULL);
COMMIT;
""")
success('retry kept ended time', 'SELECT ended_at FROM conversations WHERE id=1;', ended)
rejected('rating out of range', 'UPDATE conversations SET self_rating=6 WHERE id=1;')
success('insufficient data is a valid terminal state', "UPDATE conversations SET ended_at=NOW(6),review_status='INSUFFICIENT_DATA',summary='Not enough content' WHERE id=2;")
rejected('insufficient cannot assert level', "UPDATE conversations SET recommended_level='A1' WHERE id=2;")
success('selectively save one word', "INSERT INTO saved_words(id,user_id,word,meaning) SELECT 1,1,word,meaning FROM word_suggestions WHERE id=1; SELECT COUNT(*) FROM saved_words;", '1')
rejected('duplicate personal spelling', "INSERT INTO saved_words(user_id,word,meaning) VALUES(1,'Reservation','different');", 1062)
rejected('blank meaning', "UPDATE saved_words SET meaning=' ' WHERE id=1;")
rejected('learning needs reviewed time', "UPDATE saved_words SET status='LEARNING' WHERE id=1;")
success('self review status', "UPDATE saved_words SET status='LEARNING',last_reviewed_at=NOW(6) WHERE id=1;")
rejected('NEW clears reviewed time', "UPDATE saved_words SET status='NEW' WHERE id=1;")
success('practice snapshot', "INSERT INTO word_practices(id,conversation_id,saved_word_id,word_snapshot,meaning_snapshot) VALUES(1,1,1,'reservation','dat cho');")
rejected('USED requires evidence', "UPDATE word_practices SET result='USED',feedback='Test' WHERE id=1;")
rejected('PENDING no evidence', 'UPDATE word_practices SET evidence_message_id=1 WHERE id=1;')
rejected('nonpending requires feedback', "UPDATE word_practices SET result='UNVERIFIED' WHERE id=1;")
rejected('evidence FK', "UPDATE word_practices SET result='USED',feedback='Test',evidence_message_id=999 WHERE id=1;", 1452)
boundary('Service must check evidence belongs to same conversation', "UPDATE word_practices SET result='USED',feedback='Test',evidence_message_id=3 WHERE id=1; SELECT evidence_message_id FROM word_practices WHERE id=1;", '3')
boundary('Service must check evidence sender USER', "UPDATE word_practices SET result='USED',feedback='Test',evidence_message_id=2 WHERE id=1; SELECT evidence_message_id FROM word_practices WHERE id=1;", '2')
boundary('Service must check saved word ownership', "UPDATE word_practices SET conversation_id=2 WHERE id=1; SELECT conversation_id FROM word_practices WHERE id=1;", '2')
success('verified practice', "UPDATE word_practices SET result='USED',feedback='Test evidence only',evidence_message_id=1 WHERE id=1;")
rejected('evidence message retained', 'DELETE FROM messages WHERE id=1;', 1451)
success('delete personal word preserves history', "DELETE FROM saved_words WHERE id=1; SELECT saved_word_id IS NULL,word_snapshot,meaning_snapshot,result,evidence_message_id FROM word_practices WHERE id=1;", '1\treservation\tdat cho\tUSED\t1')
success('saved word deletion keeps suggestions', 'SELECT COUNT(*) FROM word_suggestions;', '2')
success('only full reports counted', "SELECT COUNT(*) FROM conversations WHERE review_status='COMPLETED';", '1')
print(json.dumps({'mysql_version': version, 'passed': len(passed), 'service_boundary_probes': len(boundaries),
                  'boundary_details': boundaries, 'scope': 'SQL only; no Service/JPA/AI/Android tests'}, ensure_ascii=False, indent=2))
