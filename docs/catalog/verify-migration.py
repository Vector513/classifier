"""Проверка сохранности при перезапуске отдельной тестовой копии.
python3 docs/catalog/verify-migration.py prepare|check http://127.0.0.1:18084 /tmp/catalog-restart-state.json
prepare изменяет только тестовую копию; URL с портом 8080 запрещён.
"""
import json,sys,urllib.request,urllib.error
mode,url,statefile=sys.argv[1:]
if urllib.parse.urlparse(url).port==8080:
    raise SystemExit('Для проверки нужна отдельная тестовая копия, не рабочий сайт.')
def req(path,method='GET',data=None):
    body=None if data is None else json.dumps(data).encode()
    request=urllib.request.Request(url+'/api/v1'+path,data=body,method=method,headers={'Content-Type':'application/json'})
    with urllib.request.urlopen(request) as r:
        text=r.read()
        return json.loads(text) if text else None
def flat(nodes):
    return [v for n in nodes for v in [n,*flat(n.get('children',[]))]]
if mode=='prepare':
    nodes=flat(req('/nodes/tree'))
    model=next(n for n in nodes if n['code']=='PHONES-APPLE-IP16')
    sku=next(n for n in nodes if n['code']=='SKU-IP16-BLACK-128')
    color=next(e for e in req(f"/nodes/{model['id']}/enumerations/effective") if e['enumerationCode']=='PHONE-COLORS')
    ids={v['code']:v['id'] for v in color['values']}
    group=req('/nodes','POST',{'code':'CHECK-USER-GROUP','name':'Сохранённая пользовательская группа','nodeKind':'GROUP'})
    req(f"/nodes/{sku['id']}/attributes",'PUT',{'enumerationId':color['enumerationId'],'valueId':ids['WHITE']})
    req(f"/nodes/{model['id']}/enumeration-bindings/{color['enumerationId']}",'PUT',{'allowedValueIds':[ids['BLACK'],ids['WHITE']]})
    draft=next(s for s in req('/bom/specifications') if s['productCode']=='BOM-HOUSING' and s['status']=='DRAFT')
    req(f"/bom/specifications/{draft['id']}",'DELETE')
    state={'group':group['id'],'model':model['id'],'sku':sku['id'],'enum':color['enumerationId'],'white':ids['WHITE'],'options':[ids['BLACK'],ids['WHITE']],'deletedDraft':draft['id'],'nodeCount':len(flat(req('/nodes/tree')))}
    with open(statefile,'w') as f:json.dump(state,f)
    print('Подготовлены правка свойства, сужение вариантов, удаление черновика и новая группа в тестовой копии.')
elif mode=='check':
    with open(statefile) as f:s=json.load(f)
    assert req(f"/nodes/{s['group']}")['nodeKind']=='GROUP'
    selected=req(f"/nodes/{s['sku']}/attributes")
    assert next(v for v in selected if v['enumerationId']==s['enum'])['selectedValueId']==s['white']
    options=next(v for v in req(f"/nodes/{s['model']}/enumerations/effective") if v['enumerationId']==s['enum'])
    assert sorted(v['id'] for v in options['values'])==sorted(s['options'])
    try:req(f"/bom/specifications/{s['deletedDraft']}")
    except urllib.error.HTTPError as e:assert e.code==404
    else:raise AssertionError('Удалённый черновик восстановлен')
    assert len(flat(req('/nodes/tree')))==s['nodeCount']
    print('PASS: пользовательские изменения сохранены, удалённый пример не восстановлен, дубликатов нет.')
else:raise SystemExit('Режим: prepare или check')
