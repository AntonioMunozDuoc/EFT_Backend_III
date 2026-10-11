"""Verificacion independiente de los jobs batch (equivalencia con el legacy).

Recalcula, con las MISMAS reglas de negocio pero en Python puro, los resultados esperados de
los 3 procesos para cada dataset. Se compara contra GET /api/batch/resultados y los CSV de reportes.

Uso:  python3 scripts/verificar-equivalencia.py batch-service/src/main/resources/data semana_1 semana_3
"""
import csv,re,sys,collections,json
from decimal import Decimal,ROUND_HALF_UP
from datetime import datetime
def fecha(t):
    t=(t or '').strip()
    for pat,fmt in [(r'\d{4}-\d{2}-\d{2}','%Y-%m-%d'),(r'\d{4}/\d{2}/\d{2}','%Y/%m/%d'),(r'\d{2}-\d{2}-\d{4}','%d-%m-%Y'),(r'\d{2}/\d{2}/\d{4}','%d/%m/%Y')]:
        if re.fullmatch(pat,t):
            try: return datetime.strptime(t,fmt).date()
            except: pass
    raise ValueError('fecha')
def monto(t):
    t=(t or '').strip()
    if not t: raise ValueError('vacio')
    return Decimal(t)
def rows(p):
    with open(p,encoding='utf-8') as f:
        r=csv.reader(f); next(r); return list(r)
out={}
DATA=sys.argv[1]
for ds in sys.argv[2:]:
    res={}
    # job1
    ok=rej=an=0; reasons=collections.Counter(); deb=cred=Decimal(0)
    for r in rows(f'{DATA}/{ds}/movimientos_financieros_diarios.csv'):
        try:
            if len(r)!=4: raise ValueError('cols')
            int(r[0].strip()); fecha(r[1]); m=monto(r[2]); t=r[3].strip().lower()
            if t not in('debito','credito'): raise ValueError('tipo')
            ok+=1
            if m<=0: an+=1
            if t=='debito': deb+=m
            else: cred+=m
        except Exception as e: rej+=1; reasons[str(e)]+=1
    res['job1']=dict(leidos=ok+rej,escritos=ok,omitidos=rej,anomalias=an,total_debitos=str(deb),total_creditos=str(cred),motivos=dict(reasons))
    # job2
    tasas={'ahorro':Decimal('0.0150'),'prestamo':Decimal('0.0500'),'hipoteca':Decimal('0.0400')}
    seen=set(); ok=rej=filt=0; tot=Decimal(0); reasons=collections.Counter()
    for r in rows(f'{DATA}/{ds}/intereses_trimestrales.csv'):
        try:
            if len(r)!=5: raise ValueError('cols')
            cid=int(r[0].strip()); t=r[4].strip().lower()
            if t not in tasas: raise ValueError('tipo')
            s=monto(r[2])
            if s<0: raise ValueError('saldo<0')
            if r[3].strip():
                e=int(r[3].strip())
                if e<18 or e>110: raise ValueError('edad')
            if cid in seen: filt+=1; continue
            seen.add(cid); ok+=1; tot+=(s*tasas[t]/12).quantize(Decimal('0.01'),ROUND_HALF_UP)
        except Exception as e: rej+=1; reasons[str(e)]+=1
    res['job2']=dict(leidos=ok+rej+filt,escritos=ok,filtrados_duplicados=filt,omitidos=rej,interes_mensual_total=str(tot),motivos=dict(reasons))
    # job3
    seen=set(); ok=rej=0; reasons=collections.Counter(); per=collections.defaultdict(Decimal); filt=0
    for r in rows(f'{DATA}/{ds}/estados_financieros_anuales.csv'):
        try:
            if len(r)!=5: raise ValueError('cols')
            cid=int(r[0].strip()); d=fecha(r[1]); tx=r[2].strip().lower().replace('ó','o')
            if tx not in('deposito','retiro','compra','pago'): raise ValueError('tx')
            m=monto(r[3])
            if m==0: raise ValueError('cero')
            n=abs(m) if tx=='deposito' else -abs(m)
            desc=r[4].strip() or 'Sin descripcion'
            k=(cid,str(d),tx,str(n),desc)
            ok+=1
            if k in seen: filt+=1; continue
            seen.add(k); per[(cid,d.year)]+=n
        except Exception as e: rej+=1; reasons[str(e)]+=1
    res['job3']=dict(leidos=ok+rej,validos=ok,duplicados_colapsados=filt,filas_unicas=len(seen),omitidos=rej,cuentas_anio=len(per),saldo_neto_total=str(sum(per.values())),motivos=dict(reasons))
    out[ds]=res
print(json.dumps(out,indent=1,ensure_ascii=False))
