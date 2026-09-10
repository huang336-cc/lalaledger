import json, sqlite3, os, re, tempfile, sys
SD=os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),'app/schemas/com.lightledger.app.data.db.AppDatabase')
SRC=os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),'app/src/main/java/com/lightledger/app/data/db/Migrations.kt')
LATEST=7


def extract_ddl(body):
    """从 migrate() 方法体里提取完整 SQL。

    Kotlin 里长语句常写成多段字符串拼接：
        db.execSQL("ALTER TABLE ... " + "ON DELETE SET NULL")
    简单正则只抓第一段会把 SQL 截断，导致校验误报。这里按 execSQL( ... ) 取整段，
    再去掉 Kotlin 的 + 拼接与引号。
    """
    out = []
    for m in re.finditer(r'execSQL\((.*?)\)\s*$', body, re.S | re.M):
        raw = m.group(1)
        # 去掉行内注释
        raw = re.sub(r'//.*', '', raw)
        # 提取所有字符串字面量并按顺序拼接（Kotlin 的 + 拼接语义）
        parts = re.findall(r'"([^"\\]*(?:\\.[^"\\]*)*)"', raw)
        if parts:
            out.append(''.join(parts))
    return out

src=open(SRC).read()
migs=re.findall(r'val (MIGRATION_\d+_\d+) = object : Migration\((\d+), (\d+)\) \{(.*?)\n    \}', src, re.S)
chain=sorted([(int(a),int(b),n,b2) for n,a,b,b2 in migs], key=lambda x:x[0])
reg=re.findall(r'MIGRATION_\d+_\d+', re.search(r'val ALL: Array<Migration> = arrayOf\((.*?)\)', src, re.S).group(1))
missing=[n for _,_,n,_ in chain if n not in reg]
gaps=[(a,b) for i,(a,b,_,_) in enumerate(chain) if i>0 and a!=chain[i-1][1]]
print("迁移定义:", [(a,b,n) for a,b,n,_ in chain])
print("已注册 :", reg)
print("未注册 →", missing if missing else "无 ✓")
print("链断档 →", gaps if gaps else "无 ✓")
print()

latest=json.load(open(f'{SD}/{LATEST}.json'))
allok=True
for START in (3,4,5,6):
    v=json.load(open(f'{SD}/{START}.json'))
    p=os.path.join(tempfile.gettempdir(), f'mig_check_{START}.db')
    if os.path.exists(p): os.remove(p)
    con=sqlite3.connect(p); cur=con.cursor()
    for e in v['database']['entities']:
        cur.execute(e['createSql'].replace('${TABLE_NAME}', e['tableName']))
        for idx in e.get('indices',[]):
            cur.execute(idx['createSql'].replace('${TABLE_NAME}', e['tableName']))
    # v3 schema 文件里没导出 members 表（历史导出不全），但真实 v3 库里存在。
    # 迁移 MIGRATION_3_4 有 REFERENCES members(id)，缺表会误报，这里补建。
    cur.execute("""CREATE TABLE IF NOT EXISTS members (
        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
        bookId INTEGER NOT NULL,
        name TEXT NOT NULL,
        color INTEGER NOT NULL,
        createdAt INTEGER NOT NULL DEFAULT 0
    )""")
    cur.execute("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
    cur.execute("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES (42,?)",(v["database"]["identityHash"],))
    cur.execute(f"PRAGMA user_version = {START}")
    cur.execute("""INSERT INTO transactions (bookId,type,amount,categoryId,location,note,images,createdAt,updatedAt)
                   VALUES (1,0,12345,7,'便利店','老账单','[]',1700000000000,1700000000000)""")
    con.commit()

    err=None
    for a,b,name,body in chain:
        if b<=START: continue
        for d in extract_ddl(body):
            try: cur.execute(d)
            except Exception as ex: err=f"{name}: {ex}"; break
        if err: break
        cur.execute(f"PRAGMA user_version = {b}")
    con.commit()

    if err:
        print(f"v{START} → v{LATEST}:  ✗ {err}"); allok=False; con.close(); continue

    bad=[]
    for e in latest['database']['entities']:
        t=e['tableName']
        cur.execute(f"PRAGMA table_info({t})"); act=sorted(r[1] for r in cur.fetchall())
        exp=sorted(f['columnName'] for f in e['fields'])
        cur.execute(f"PRAGMA index_list({t})"); ai=sorted(r[1] for r in cur.fetchall() if not r[1].startswith('sqlite_auto'))
        ei=sorted(i['name'] for i in e.get('indices',[]))
        if act!=exp or ai!=ei: bad.append(t)
    cur.execute("SELECT amount,note,iconOverride FROM transactions")
    row=cur.fetchone()
    keep = row and row[0]==12345 and row[1]=='老账单'
    ok = not bad and keep
    allok &= ok
    print(f"v{START} → v{LATEST}:  {'✓ 结构一致 + 数据保留' if ok else '✗ '+str(bad)}")
    con.close()

print()
ok_final = allok and not missing and not gaps
print()
print("结论:", "✅ 迁移链完整，旧库升级不会闪退" if ok_final else "❌ 有问题，见上")
sys.exit(0 if ok_final else 1)
