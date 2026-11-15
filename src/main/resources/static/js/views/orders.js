import {esc, openForm, openDialog, confirmDialog, loadingState, toast} from '../ui.js';
import {navigate} from '../router.js';
const base='/api/v1/orders';
const types=[['NUMBER','Число'],['INTEGER','Целое число'],['STRING','Текст'],['BOOLEAN','Да / нет'],['ENUM','Перечисление']];
const ops=[['AND','И'],['OR','ИЛИ'],['NOT','НЕ'],['IMPLIES','Если … то …'],['EQ','Равно'],['NE','Не равно'],['GT','Больше'],['GE','Больше или равно'],['LT','Меньше'],['LE','Меньше или равно'],['IN','Входит в список'],['ADD','Сложение'],['SUB','Вычитание'],['MUL','Умножение'],['DIV','Деление'],['MOD','Остаток'],['MIN','Минимум'],['MAX','Максимум'],['ABS','Модуль'],['NEG','Смена знака']];
const btn=(label,action,id='',primary=false)=>`<button type="button" class="btn ${primary?'btn--primary':'btn--secondary'}" data-action="${action}" data-id="${id}">${esc(label)}</button>`;
const field=(name,label,value='',required=true)=>({name,label,value:value??'',required,full:true});
const select=(name,label,items,value='',required=true)=>({...field(name,label,value,required),type:'select',options:[{value:'',label:'Выбрать…'},...items.map(([value,label])=>({value,label}))]});
const table=(heads,rows)=>rows.length?`<div class="table-wrap"><table class="table"><thead><tr>${heads.map(s=>`<th>${esc(s)}</th>`).join('')}</tr></thead><tbody>${rows.map(row=>`<tr>${row.map((cell,i)=>`<td data-label="${esc(heads[i])}"><div class="order-cell-value">${cell}</div></td>`).join('')}</tr>`).join('')}</tbody></table></div>`:'<p class="orders-empty">Пока нет записей.</p>';
const idQuery=row=>row?`?id=${row.id}`:'';
const number=(raw,label='Число')=>{const v=Number(String(raw).replace(',','.'));if(String(raw).trim()===''||!Number.isFinite(v))throw new Error(`${label}: введите число.`);return v;};
const integer=(raw)=>{const v=number(raw,'Количество');if(!Number.isSafeInteger(v)||v<1||v>2147483647)throw new Error('Количество должно быть положительным целым числом.');return v;};
async function request(path='',method='GET',body){
 const r=await fetch(base+path,{method,headers:body?{'Content-Type':'application/json'}:{},body:body?JSON.stringify(body):undefined});
 if(!r.ok){const e=await r.json().catch(()=>null);throw new Error(e?.message||`Не удалось выполнить действие (${r.status}).`);}
 return r.status===204?null:r.json();
}
const constant=value=>({kind:'CONST',value,args:[]});
const clone=v=>JSON.parse(JSON.stringify(v));
const comparisons=[
 ['GE','Больше или равно','≥'], ['GT','Больше','>'],
 ['LE','Меньше или равно','≤'], ['LT','Меньше','<'],
 ['EQ','Равно','='], ['NE','Не равно','≠'],
];
const functionModes=[
 ['comparison','Сравнить два числа'],
 ['range','Проверить число в диапазоне'],
 ['advanced','Составить сложное выражение'],
];
const quickLabels={
 comparison:['Первое число','Второе число'],
 range:['Проверяемое число','Нижняя граница','Верхняя граница'],
};

function quickFunctionMode(row){
 if(row?.result_type!=='BOOLEAN'||!row.parameters.every(p=>['NUMBER','INTEGER'].includes(p.data_type??p.dataType)))return null;
 const codes=row.parameters.map(p=>p.code);
 const expression=row.expression;
 const compares=(node,left,right)=>node?.kind==='OPERATOR'&&node.args.length===2&&
  node.args.every((arg,index)=>arg.kind==='FORMAL'&&arg.formalCode===(index===0?left:right));
 if(codes.length===2&&compares(expression,...codes)&&comparisons.some(([op])=>op===expression.operator))return 'comparison';
 if(codes.length!==3||expression?.operator!=='AND'||expression.args.length!==2)return null;
 const [value,lower,upper]=codes;
 const [lowerCheck,upperCheck]=expression.args;
 return lowerCheck.operator==='GE'&&upperCheck.operator==='LE'&&
  compares(lowerCheck,value,lower)&&compares(upperCheck,value,upper)?'range':null;
}

function quickFunctionDraft(mode,operator='GE'){
 const codes=mode==='comparison'?['first','second']:['value','lower','upper'];
 const [value,lower,upper]=codes;
 const compare=(op,left,right)=>({
  kind:'OPERATOR',operator:op,args:[left,right].map(formalCode=>({kind:'FORMAL',formalCode,args:[]})),
 });
 return {
  resultType:'BOOLEAN',
  parameters:codes.map(code=>({code,dataType:'NUMBER'})),
  expression:mode==='comparison'?compare(operator,value,lower):{
   kind:'OPERATOR',operator:'AND',args:[compare('GE',value,lower),compare('LE',value,upper)],
  },
 };
}

function quickFunctionSummary(mode,operator){
 if(mode==='range')return 'Нижняя граница ≤ проверяемое число ≤ верхняя граница';
 const symbol=comparisons.find(([op])=>op===operator)[2];
 return `Первое число ${symbol} второе число`;
}

const expressionText=(e,functions=[])=>{
 if(e.kind==='CONST')return JSON.stringify(e.value);
 if(e.kind==='PARAMETER')return e.parameterCode;
 if(e.kind==='FORMAL')return e.formalCode;
 const name=e.kind==='CALL'?(functions.find(f=>f.id===e.functionId)?.code||'Функция'):e.operator;
 return `${name}(${(e.args||[]).map(v=>expressionText(v,functions)).join(', ')})`;
};
function validateExpression(e,depth=0){
 if(depth>40)throw new Error('Выражение слишком глубокое.');
 if(e.kind==='CONST'&&(e.value==null||typeof e.value==='number'&&!Number.isFinite(e.value)))throw new Error('Заполните константу выражения.');
 if(e.kind==='PARAMETER'&&!e.parameterCode)throw new Error('Выберите параметр изделия.');
 if(e.kind==='FORMAL'&&!e.formalCode)throw new Error('Выберите аргумент функции.');
 if(e.kind==='CALL'&&!e.functionId)throw new Error('Выберите вызываемую функцию.');
 (e.args||[]).forEach(a=>validateExpression(a,depth+1));return e;
}
// Дерево редактируется элементами формы, без ввода JSON и выполнения кода.
function expressionEditor(model,context,depth=0){
 const root=document.createElement('div');root.className='order-expression';root.dataset.exprNode='';
 function draw(){
  root.replaceChildren();
  const choices=[['CONST','Константа'],['OPERATOR','Операция'],['CALL','Вызов функции']];
  if(context.parameters?.length)choices.push(['PARAMETER','Параметр изделия']);
  if(context.formals?.length)choices.push(['FORMAL','Аргумент функции']);
  function choice(label,items,value,onChange){
   const wrap=document.createElement('label');wrap.className='order-control';wrap.append(document.createTextNode(label));
   const input=document.createElement('select');input.className='select';items.forEach(([v,s])=>input.add(new Option(s,v)));input.value=String(value??'');input.addEventListener('change',()=>onChange(input.value));wrap.append(input);return wrap;
  }
  const head=document.createElement('div');head.className='order-expression-head';
  head.append(choice('Вид выражения',choices,model.kind,kind=>{
   Object.keys(model).forEach(k=>delete model[k]);Object.assign(model,{kind,args:[]});
   if(kind==='CONST')model.value=0;
   if(kind==='OPERATOR'){model.operator='GT';model.args=[constant(0),constant(0)];}
   if(kind==='PARAMETER')model.parameterCode=context.parameters[0].code;
   if(kind==='FORMAL')model.formalCode=context.formals[0].code;
   if(kind==='CALL'){model.functionId=context.functions?.[0]?.id;model.args=(context.functions?.[0]?.parameters||[]).map(()=>constant(0));}
   draw();
  }));root.append(head);
  if(model.kind==='OPERATOR')head.append(choice('Операция',ops,model.operator,v=>{model.operator=v;const unary=['NOT','ABS','NEG'].includes(v);if(unary)model.args=model.args.slice(0,1);while(model.args.length<(unary?1:2))model.args.push(constant(0));draw();}));
  if(model.kind==='PARAMETER')head.append(choice('Параметр',context.parameters.map(p=>[p.code,`${p.name} (${p.code})`]),model.parameterCode,v=>model.parameterCode=v));
  if(model.kind==='FORMAL')head.append(choice('Формальный аргумент',context.formals.map(p=>[p.code,`${p.code} — ${types.find(t=>t[0]===p.dataType)?.[1]||p.dataType}`]),model.formalCode,v=>model.formalCode=v));
  if(model.kind==='CALL'){
   head.append(choice('Функция',[['','Выбрать…'],...(context.functions||[]).map(f=>[f.id,f.name])],model.functionId,v=>{model.functionId=Number(v)||null;model.args=(context.functions.find(f=>f.id===model.functionId)?.parameters||[]).map(()=>constant(0));draw();}));
   const fun=context.functions?.find(f=>f.id===model.functionId);if(fun){const p=document.createElement('p');p.className='orders-empty';p.textContent=`Аргументы по порядку: ${fun.parameters.map(v=>v.code).join(', ')||'без аргументов'}`;root.append(p);}
  }
  if(model.kind==='CONST'){
   const kind=Array.isArray(model.value)?'LIST':typeof model.value==='boolean'?'BOOLEAN':typeof model.value==='string'?'STRING':'NUMBER';
   const constantTypes=[...types.filter(([type])=>['NUMBER','STRING','BOOLEAN'].includes(type)),['LIST','Список значений']];
   head.append(choice('Тип константы',constantTypes,kind,v=>{
    model.value={BOOLEAN:true,STRING:'',NUMBER:0,LIST:[]}[v];draw();
   }));
   if(kind==='BOOLEAN')head.append(choice('Значение',[['true','Да'],['false','Нет']],String(model.value),v=>model.value=v==='true'));
   else if(kind==='LIST'){
    const wrap=document.createElement('label');
    wrap.className='order-control';
    wrap.append('Список значений');
    const input=document.createElement('textarea');
    input.className='input';
    input.rows=2;
    input.value=JSON.stringify(model.value);
    input.placeholder='[16, 32, 64]';
    input.addEventListener('input',()=>{
     try{
      const values=JSON.parse(input.value);
      if(!Array.isArray(values))throw new Error();
      model.value=values;
      input.setCustomValidity('');
     }catch{
      input.setCustomValidity('Введите список в квадратных скобках, например [16, 32, 64].');
     }
    });
    wrap.append(input);
    head.append(wrap);
   }
   else{const wrap=document.createElement('label');wrap.className='order-control';wrap.append(document.createTextNode('Значение константы'));const input=document.createElement('input');input.className='input';input.value=model.value??'';input.addEventListener('input',()=>{model.value=kind==='NUMBER'?(input.value.trim()===''?null:Number(input.value.replace(',','.'))):input.value;});wrap.append(input);head.append(wrap);}
  }
  if(['CALL','OPERATOR'].includes(model.kind)){
   const children=document.createElement('div');children.className='order-expression-args';
   (model.args||[]).forEach((arg,i)=>{
    const box=document.createElement('div');box.className='order-argument';const controls=document.createElement('div');controls.className='order-expression-head';const label=document.createElement('strong');label.textContent=`Аргумент ${i+1}`;controls.append(label);
    for(const [name,fn,disabled] of [['Выше',()=>{[model.args[i-1],model.args[i]]=[model.args[i],model.args[i-1]];draw();},i===0],['Ниже',()=>{[model.args[i+1],model.args[i]]=[model.args[i],model.args[i+1]];draw();},i===model.args.length-1],['Убрать',()=>{model.args.splice(i,1);draw();},false]]){
     const b=document.createElement('button');b.type='button';b.className='btn btn--secondary';b.textContent=name;b.disabled=disabled;b.addEventListener('click',fn);controls.append(b);
    }
    box.append(controls,expressionEditor(arg,context,depth+1));children.append(box);
   });root.append(children);
   const add=document.createElement('button');add.type='button';add.className='btn btn--secondary';add.textContent='Добавить аргумент';add.disabled=depth>=35;add.addEventListener('click',()=>{model.args.push(constant(0));draw();});root.append(add);
  }
 }
 draw();return root;
}
function expressionDialog({title,row,context,isFunction,onSave,onSaved}){
  const existingMode=isFunction?quickFunctionMode(row):null;
  let mode=isFunction?(existingMode||(row?'advanced':'comparison')):'advanced';
  const drafts={
   comparison:quickFunctionDraft('comparison'),
   range:quickFunctionDraft('range'),
  };
  if(row||!isFunction){
   drafts[mode]={
    expression:clone(row?.expression||{kind:'OPERATOR',operator:'GT',args:[constant(1),constant(0)]}),
    parameters:(row?.parameters||[]).map(p=>({code:p.code,dataType:p.data_type??p.dataType})),
    resultType:row?.result_type||'BOOLEAN',
   };
  }
  const code=row?.code??(isFunction?`FN_${Date.now()}_${crypto.getRandomValues(new Uint32Array(1))[0].toString(36)}`:'');
  return openDialog({title,wide:true,build(api){
   const modes=row?functionModes.filter(([value])=>value===existingMode||value==='advanced'):functionModes;
   const options=(items,selected)=>items.map(([value,label])=>
    `<option value="${value}" ${value===selected?'selected':''}>${esc(label)}</option>`
   ).join('');
   const codeField=`<label class="order-control">Код
    <input class="input" name="code" maxlength="100" required value="${esc(code)}">
   </label>`;
   api.body.innerHTML=`
    <form class="orders-editor-form">
     <label class="order-control">Название
      <input class="input" name="name" maxlength="255" required value="${esc(row?.name||'')}">
     </label>
     ${isFunction?'':codeField}
     ${isFunction&&modes.length>1?`<div class="order-control">
      <label for="order-function-mode">Что должна делать функция?</label>
      <select class="select" id="order-function-mode" name="functionMode">${options(modes,mode)}</select>
     </div>`:''}
     ${isFunction?'':`<label class="order-control">Сообщение об ошибке
      <input class="input" name="errorMessage" required value="${esc(row?.error_message||'Конфигурация не соответствует правилу')}">
     </label>`}
     <div class="order-function-body" data-function-body></div>
     ${isFunction?`<details class="order-function-code">
      <summary>Код функции</summary>
      ${codeField}
     </details>`:''}
     <p class="orders-error" role="alert" data-editor-error hidden></p>
    </form>`;
   const form=api.body.querySelector('form');
   const error=form.querySelector('[data-editor-error]');
   const body=form.querySelector('[data-function-body]');
   const codeDetails=form.querySelector('.order-function-code');
   if(codeDetails)form.elements.code.addEventListener('invalid',()=>{codeDetails.open=true;});
   const modeSelect=form.elements.namedItem('functionMode');
   modeSelect?.addEventListener('change',()=>{
    if(!drafts[modeSelect.value])drafts[modeSelect.value]=clone(drafts[mode]);
    mode=modeSelect.value;
    error.hidden=true;
    draw();
   });
   function draw(){
    const draft=drafts[mode];
    if(mode!=='advanced'){
     body.innerHTML=`<section class="order-function-preset">
      ${mode==='comparison'?`<div class="order-control">
       <label for="order-function-comparison">Как сравнить числа?</label>
       <select class="select" id="order-function-comparison" name="comparison">${options(comparisons,draft.expression.operator)}</select>
      </div>`:''}
      <p class="order-function-preview" data-quick-preview aria-live="polite"></p>
      ${mode==='range'?'<p class="orders-empty">Границы задаются при вызове и входят в диапазон.</p>':''}
      <p class="orders-empty">Результат проверки — да или нет.</p>
     </section>`;
     const preview=body.querySelector('[data-quick-preview]');
     const updatePreview=()=>{preview.textContent=quickFunctionSummary(mode,draft.expression.operator);};
     form.elements.namedItem('comparison')?.addEventListener('change',event=>{
      draft.expression.operator=event.target.value;
      updatePreview();
     });
     updatePreview();
     return;
    }
    body.innerHTML=`${isFunction?`<label class="order-control">Тип результата
     <select class="select" name="resultType">${options(types,draft.resultType)}</select>
    </label><section data-signature></section>`:''}
    <h3>${isFunction?'Выражение функции':'Условие допустимости'}</h3><div data-expression></div>`;
    const drawExpression=()=>body.querySelector('[data-expression]').replaceChildren(
     expressionEditor(draft.expression,{...context,formals:draft.parameters})
    );
    if(isFunction){
     form.elements.resultType.addEventListener('change',event=>{draft.resultType=event.target.value;});
     const area=body.querySelector('[data-signature]');
     area.innerHTML='<h3>Входные аргументы</h3>';
     draft.parameters.forEach((parameter,index)=>{
      const line=document.createElement('div');
      line.className='order-signature-line';
      line.innerHTML=`<label class="order-control">Аргумент ${index+1}
       <input class="input" value="${esc(parameter.code)}" aria-label="Код аргумента ${index+1}" required>
      </label><label class="order-control">Тип
       <select class="select" aria-label="Тип аргумента ${index+1}">${options(types,parameter.dataType)}</select>
      </label><button type="button" class="btn btn--secondary">Убрать</button>`;
      const codeInput=line.querySelector('input');
      const validCode=()=>{
       const code=codeInput.value.trim();
       const duplicate=draft.parameters.some(p=>p!==parameter&&p.code===code);
       codeInput.setCustomValidity(duplicate?'Такой код аргумента уже есть.':'');
       return !duplicate;
      };
      codeInput.addEventListener('input',validCode);
      codeInput.addEventListener('change',()=>{
       if(!validCode())return;
       const previous=parameter.code;
       parameter.code=codeInput.value.trim();
       function rename(node){
        if(node.kind==='FORMAL'&&node.formalCode===previous)node.formalCode=parameter.code;
        node.args?.forEach(rename);
       }
       rename(draft.expression);
       drawExpression();
      });
      line.querySelector('select').addEventListener('change',event=>{
       parameter.dataType=event.target.value;
       drawExpression();
      });
      line.querySelector('button').onclick=()=>{draft.parameters.splice(index,1);draw();};
      area.append(line);
     });
     const add=document.createElement('button');
     add.type='button';
     add.className='btn btn--secondary';
     add.textContent='Добавить аргумент функции';
     add.onclick=()=>{
      let index=draft.parameters.length+1;
      while(draft.parameters.some(p=>p.code===`arg${index}`))index++;
      draft.parameters.push({code:`arg${index}`,dataType:'NUMBER'});
      draw();
     };
     area.append(add);
    }
    drawExpression();
   }
   draw();
   api.addButton({label:'Отмена',onClick(){api.close();}});
   api.addButton({label:'Сохранить',variant:'btn--primary',async onClick(_,button){
    if(!form.reportValidity())return;
    error.hidden=true;
    button.disabled=true;
    try{
     const values=new FormData(form);
     const draft=drafts[mode];
     const data={code:values.get('code').trim(),name:values.get('name').trim(),expression:validateExpression(draft.expression)};
     if(isFunction)Object.assign(data,{resultType:draft.resultType,parameters:draft.parameters});
     else data.errorMessage=values.get('errorMessage').trim();
     await onSave(data);
     api.close();
     await onSaved?.();
    }catch(e){
     error.textContent=e.message;
     error.hidden=false;
    }finally{
     button.disabled=false;
    }
   }});
  }});
}

let sequence=0;
export async function render(container,route){
 const ticket=++sequence,hash=location.hash;const active=()=>ticket===sequence&&hash===location.hash;
 const refresh=()=>active()?render(container,route):Promise.resolve();
 container.innerHTML=`<div class="page">${loadingState('Загрузка заказов и конфигуратора…')}</div>`;
 const data=await request();if(!active())return;
 const section=route.segments[1]||'orders';
 const templateId=section==='templates'?Number(route.segments[2])||null:null;
 const orderId=/^\d+$/.test(section)?Number(section):null;
 const detail=templateId?await request(`/templates/${templateId}`):orderId?await request(`/${orderId}`):null;if(!active())return;
 const pairs=(rows,label=x=>x.name)=>rows.map(x=>[x.id,label(x)]);
 const ready=data.catalog.filter(n=>n.node_kind==='PRODUCT'&&!n.has_template);
 const fieldRow=(v,unit)=>`${esc(v??'—')}${unit?' '+esc(unit):''}`;
 const actions=(edit,del,id)=>`<div class="order-actions">${btn('Изменить',edit,id)}${btn('Удалить',del,id)}</div>`;
 async function form(title,fields,onSubmit,extra={}){const result=await openForm({title,fields,wide:true,onSubmit,...extra});if(result)await refresh();return result;}
 async function remove(message,fn){if(await confirmDialog({title:'Удалить запись?',message})){await fn();await refresh();}}
 async function customerForm(row){return form(row?'Изменить заказчика':'Новый заказчик',[field('name','Имя или наименование',row?.name),field('contactPerson','Контактное лицо',row?.contact_person,false),field('phone','Телефон',row?.phone,false),field('email','Электронная почта',row?.email,false)],v=>request('/customers'+idQuery(row),'POST',v),{subtitle:'Заполни хотя бы один способ связи: телефон или электронную почту.'});}
 async function orderForm(row){
  if(!data.customers.length){toast.error('Сначала добавь заказчика во вкладке «Заказчики».');return;}
  const today=new Date().toLocaleDateString('en-CA');
  return form(row?'Изменить заказ':'Создать заказ',[select('customerId','Заказчик',pairs(data.customers),row?.customer_id),field('orderNumber','Номер заказа',row?.order_number),field('orderDate','Дата оформления (ГГГГ-ММ-ДД)',row?.order_date||today),field('requestedDate','Желаемый срок (ГГГГ-ММ-ДД)',row?.requested_date,false)],async v=>{const result=await request(''+idQuery(row),'POST',{...v,customerId:Number(v.customerId),requestedDate:v.requestedDate||null});if(!row)navigate(`/orders/${result.id}`);return result;});
 }
 async function templateForm(row){return form(row?'Изменить шаблон':'Новый шаблон изделия',[select('productId','Запись классификатора',pairs(data.catalog.filter(n=>n.node_kind!=='GROUP'&&(!n.has_template||n.id===row?.product_id)),n=>`${n.name} · ${n.code}`),row?.product_id),field('code','Код шаблона',row?.code),field('name','Название шаблона',row?.name)],async v=>{const result=await request('/templates'+idQuery(row),'POST',{...v,productId:Number(v.productId)});if(!row)navigate(`/orders/templates/${result.id}`);return result;});}
 async function parameterForm(row){
  return form(row?'Изменить параметр':'Добавить параметр',[field('code','Код параметра',row?.code),field('name','Название',row?.name),select('dataType','Тип',types,row?.data_type||'NUMBER'),select('required','Обязательный',[['true','Да'],['false','Нет']],String(row?.required??true)),select('unitOfMeasureId','Единица измерения',pairs(data.units,u=>`${u.code} — ${u.name}`),row?.unit_of_measure_id,false),select('enumerationId','Перечисление',pairs(data.enumerations),row?.enumeration_id,false),field('minValue','Минимум',row?.min_value,false),field('maxValue','Максимум',row?.max_value,false)],v=>request(`/templates/${templateId}/parameters${idQuery(row)}`,'POST',{code:v.code,name:v.name,dataType:v.dataType,required:v.required==='true',unitOfMeasureId:['NUMBER','INTEGER'].includes(v.dataType)&&v.unitOfMeasureId?Number(v.unitOfMeasureId):null,enumerationId:v.dataType==='ENUM'&&v.enumerationId?Number(v.enumerationId):null,minValue:['NUMBER','INTEGER'].includes(v.dataType)&&v.minValue!==''?number(v.minValue,'Минимум'):null,maxValue:['NUMBER','INTEGER'].includes(v.dataType)&&v.maxValue!==''?number(v.maxValue,'Максимум'):null}),{onMount(form){const update=()=>{const numeric=['NUMBER','INTEGER'].includes(form.elements.dataType.value);for(const name of ['unitOfMeasureId','minValue','maxValue'])form.querySelector(`[data-field="${name}"]`).hidden=!numeric;form.querySelector('[data-field="enumerationId"]').hidden=form.elements.dataType.value!=='ENUM';};form.elements.dataType.addEventListener('change',update);update();}});
 }
 function ruleForm(row){expressionDialog({title:row?'Изменить правило':'Новое правило конфигурации',row,context:{parameters:detail.parameters,functions:data.functions},onSave:r=>request(`/templates/${templateId}/rules${idQuery(row)}`,'POST',r),onSaved:refresh});}
 function functionForm(row){expressionDialog({title:row?'Изменить функцию':'Новая функция ограничения',row,isFunction:true,context:{functions:data.functions},onSave:r=>request('/functions'+idQuery(row),'POST',r),onSaved:refresh});}
 async function readyForm(row){return form(row?'Изменить позицию':'Добавить готовое изделие',[select('productId','Готовое изделие',pairs(ready,n=>`${n.name} · ${n.code}`),row?.product_id),field('quantity','Количество',row?.quantity||1),field('note','Примечание',row?.note,false)],v=>request(`/${orderId}/lines${idQuery(row)}`,'POST',{productId:Number(v.productId),quantity:integer(v.quantity),note:v.note}));}
 async function configure(tplId,row,existingVariant){
  if(!tplId){const chosen=await openForm({title:'Выбрать шаблон изделия',fields:[select('templateId','Шаблон',pairs(data.templates))],onSubmit:v=>v});if(!chosen)return;tplId=Number(chosen.templateId);}
  const t=await request(`/templates/${tplId}`);const previous=row?.variant_id||existingVariant?await request(`/variants/${row?.variant_id||existingVariant.id}`):null;
  const fields=[field('code','Код исполнения',existingVariant?previous.variant.code:`${t.template.code.slice(0,70)}-${Date.now()}`),field('name','Название исполнения',previous?.variant.name||`${t.template.name} — исполнение`)];
  if(orderId)fields.push(field('quantity','Количество',row?.quantity||1),field('note','Примечание',row?.note,false));
  t.parameters.forEach(p=>{const value=previous?.values[p.code]??'';const name=`p_${p.id}`,label=`${p.name}${p.unit?' ('+p.unit+')':''}`;if(p.data_type==='ENUM')fields.push(select(name,label,p.options.map(v=>[v.code,v.name]),value,p.required));else if(p.data_type==='BOOLEAN')fields.push(select(name,label,[['true','Да'],['false','Нет']],value===''?'':String(value),p.required));else fields.push({...field(name,label,value,p.required),hint:p.min_value!=null||p.max_value!=null?`Допустимо: ${p.min_value??'без нижней границы'} … ${p.max_value??'без верхней границы'}`:p.data_type==='INTEGER'?'Целое число':null});});
  function valuesOf(raw){const result={};t.parameters.forEach(p=>{const rawValue=raw[`p_${p.id}`];if(rawValue==null||rawValue==='')return;result[p.code]=p.data_type==='BOOLEAN'?rawValue==='true':['NUMBER','INTEGER'].includes(p.data_type)?number(rawValue,p.name):rawValue;});return result;}
  const result=await openForm({title:row?'Задать новое исполнение позиции':'Создать вариант исполнения',wide:true,subtitle:'Система проверит параметры и правила перед сохранением.',fields,submitLabel:orderId?'Создать и включить в заказ':'Создать вариант',async onSubmit(v){const values=valuesOf(v);const checked=await request(`/templates/${tplId}/validate`,'POST',{values});if(!checked.valid)throw new Error(checked.errors.join('; '));return orderId?request(`/${orderId}/configured-lines`,'POST',{templateId:tplId,code:v.code,name:v.name,values,quantity:integer(v.quantity),note:v.note,lineId:row?.id??null}):request(existingVariant?`/variants/${existingVariant.id}`:`/templates/${tplId}/variants`,existingVariant?'PUT':'POST',{code:v.code,name:v.name,values});},onMount(form){const area=document.createElement('div');area.className='order-validation';area.innerHTML='<button class="btn btn--secondary" type="button">Проверить ограничения</button><div role="status" data-validation-result></div>';form.append(area);area.querySelector('button').onclick=async()=>{const button=area.querySelector('button'),status=area.querySelector('[data-validation-result]');button.disabled=true;try{const raw=Object.fromEntries(new FormData(form));const r=await request(`/templates/${tplId}/validate`,'POST',{values:valuesOf(raw)});status.textContent=r.valid?'Все ограничения выполнены':r.errors.join('; ');status.className=r.valid?'orders-success':'orders-error';}catch(e){status.textContent=e.message;status.className='orders-error';}finally{button.disabled=false;}};form.addEventListener('input',()=>area.querySelector('[data-validation-result]').textContent='');}});
  if(result)await refresh();
 }
 async function evaluate(row){
  const mode=quickFunctionMode(row);
  const labels=quickLabels[mode];
  const fields=row.parameters.map((p,index)=>{
   if(p.data_type==='BOOLEAN')return select(p.code,p.code,[['true','Да'],['false','Нет']],'true');
   const label=labels?.[index]??`${p.code} (${types.find(([type])=>type===p.data_type)?.[1]})`;
   return field(p.code,label);
  });
  return openForm({
   title:`Проверить функцию «${row.name}»`,
   subtitle:mode?quickFunctionSummary(mode,row.expression.operator):undefined,
   fields,
   async onSubmit(values){
    const args=row.parameters.map((p,index)=>{
     const value=values[p.code];
     if(p.data_type==='BOOLEAN')return value==='true';
     if(['NUMBER','INTEGER'].includes(p.data_type))return number(value,labels?.[index]??p.code);
     return value;
    });
    const result=await request(`/functions/${row.id}/evaluate`,'POST',{arguments:args});
    toast.success(`Результат: ${typeof result==='boolean'?(result?'Да':'Нет'):result}`);
    return result;
   },
  });
 }
 const nav=[['orders','Заказы','/orders'],['templates','Конструктор изделий','/orders/templates'],['functions','Библиотека функций','/orders/functions'],['customers','Заказчики','/orders/customers']];
 const tab=orderId?'orders':section;
 let body='';
 if(templateId){
  body=`<section class="card order-panel"><div class="order-section-head"><div><h2>${esc(detail.template.name)}</h2><p class="orders-empty">${esc(detail.template.code)}</p></div><div class="order-actions">${btn('Изменить шаблон','edit-template',templateId)}${btn('Создать вариант','create-variant',templateId,true)}</div></div><h3>Параметры конфигурации</h3>${btn('Добавить параметр','add-parameter')}${table(['Код','Параметр','Тип','Границы / варианты','Действия'],detail.parameters.map(p=>[esc(p.code),`${esc(p.name)} ${p.required?'<span class="badge">обязательный</span>':''}`,esc(types.find(t=>t[0]===p.data_type)?.[1]),p.data_type==='ENUM'?p.options.map(o=>esc(o.name)).join(', '):`${fieldRow(p.min_value,p.unit)} … ${fieldRow(p.max_value,p.unit)}`,actions('edit-parameter','delete-parameter',p.id)]))}<h3>Правила совместимости</h3>${btn('Добавить правило','add-rule')}${table(['Правило','Условие','Сообщение','Действия'],detail.rules.map(r=>[esc(r.name),`<span class="order-formula">${esc(expressionText(r.expression,data.functions))}</span>`,esc(r.error_message),actions('edit-rule','delete-rule',r.id)]))}<h3>Варианты исполнения</h3>${table(['Код','Исполнение','Значения','Действия'],detail.variants.map(v=>[esc(v.code),esc(v.name),Object.entries(v.values).map(([k,val])=>`${esc(k)}: ${esc(String(val))}`).join('<br>'),actions('edit-variant','delete-variant',v.id)]))}</section>`;
 }else if(orderId){
  const o=detail.order;body=`<section class="card order-panel"><div class="order-section-head"><div><h2>Заказ ${esc(o.order_number)}</h2><p>${esc(o.customer.name)} · ${esc(o.order_date)}${o.requested_date?' · срок '+esc(o.requested_date):''}</p></div><div class="order-actions">${btn('Изменить заказ','edit-order',orderId)}<a class="btn btn--secondary" href="${base}/${orderId}/document" target="_blank" rel="noopener">Печать заказа</a></div></div><div class="order-actions">${btn('Добавить готовое изделие','add-ready')}${btn('Добавить исполнение по шаблону','add-configured','',true)}</div>${table(['№','Изделие / исполнение','Параметры','Количество','Примечание','Действия'],detail.lines.map(l=>[String(l.line_number),`<strong>${esc(l.name)}</strong><br>${esc(l.code)}${l.variant_id?'<br><span class="badge">вариант исполнения</span>':''}`,l.parameters.map(p=>`${esc(p.name)}: ${esc(String(p.value))} ${esc(p.unit||'')}`).join('<br>')||'—',String(l.quantity),esc(l.note||''),actions('edit-line','delete-line',l.id)]))}</section>`;
 }else if(section==='templates')body=`<section class="card order-panel"><div class="order-section-head"><h2>Шаблоны изделий</h2>${btn('Новый шаблон','add-template','',true)}</div><p class="orders-empty">Задай параметры изделия и правила допустимых сочетаний. Из шаблона создаются проверенные исполнения.</p>${table(['Код','Шаблон','Действия'],data.templates.map(t=>[esc(t.code),`<a href="#/orders/templates/${t.id}">${esc(t.name)}</a>`,actions('edit-template','delete-template',t.id)]))}</section>`;
 else if(section==='functions'){
  const rows=data.functions.map(f=>{
   const mode=quickFunctionMode(f);
   const summary=mode?quickFunctionSummary(mode,f.expression.operator):expressionText(f.expression,data.functions);
   return [
    `${esc(f.name)}<br><span class="order-formula">${esc(summary)}</span>`,
    (quickLabels[mode]??f.parameters.map(p=>p.code)).map(esc).join(', '),
    esc(types.find(([type])=>type===f.result_type)?.[1]),
    `<div class="order-actions">${btn('Проверить','evaluate-function',f.id)}${btn('Изменить','edit-function',f.id)}${btn('Удалить','delete-function',f.id)}</div>`,
   ];
  });
  body=`<section class="card order-panel">
   <div class="order-section-head"><h2>Библиотека функций</h2>${btn('Новая функция','add-function','',true)}</div>
   ${table(['Функция','Входные данные','Результат','Действия'],rows)}
  </section>`;
 }
 else if(section==='customers')body=`<section class="card order-panel"><div class="order-section-head"><h2>Заказчики</h2>${btn('Новый заказчик','add-customer','',true)}</div>${table(['Заказчик','Контактное лицо','Связь','Действия'],data.customers.map(c=>[esc(c.name),esc(c.contact_person||''),`${esc(c.phone||'')}<br>${esc(c.email||'')}`,actions('edit-customer','delete-customer',c.id)]))}</section>`;
 else body=`<section class="card order-panel"><div class="order-section-head"><h2>Заказы</h2>${btn('Создать заказ','add-order','',true)}</div><p class="orders-empty">Включай готовые изделия или создавай исполнение с проверкой параметров по шаблону.</p>${table(['Номер','Дата','Заказчик','Позиций','Действия'],data.orders.map(o=>[`<a href="#/orders/${o.id}">${esc(o.order_number)}</a>`,esc(o.order_date),esc(o.customer_name),String(o.line_count),actions('edit-order','delete-order',o.id)]))}</section>`;
 container.innerHTML=`<div class="page orders-page"><div class="page-head"><div class="page-head__text"><h1>Заказы и конфигурация</h1><p>Изделия с вариантами исполнения</p></div></div><nav class="order-tabs" aria-label="Работа с заказами">${nav.map(([key,name,path])=>`<a href="#${path}" class="${tab===key?'is-selected':''}" ${tab===key?'aria-current="page"':''}>${name}</a>`).join('')}</nav>${body}</div>`;
 const byId=(rows,id)=>rows.find(r=>r.id===Number(id));
 container.querySelectorAll('[data-action]').forEach(button=>button.addEventListener('click',async()=>{
  const a=button.dataset.action,id=Number(button.dataset.id);try{
   if(a==='add-customer')await customerForm();else if(a==='edit-customer')await customerForm(byId(data.customers,id));else if(a==='delete-customer')await remove('Заказчика с заказами удалить нельзя.',()=>request(`/customers/${id}`,'DELETE'));
   else if(a==='add-order')await orderForm();else if(a==='edit-order')await orderForm(byId(data.orders,id));else if(a==='delete-order')await remove('Заказ и его позиции будут удалены. Исполнения сохранятся.',()=>request(`/${id}`,'DELETE'));
   else if(a==='add-template')await templateForm();else if(a==='edit-template')await templateForm(byId(data.templates,id));else if(a==='delete-template')await remove('Шаблон с созданными вариантами удалить нельзя.',()=>request(`/templates/${id}`,'DELETE'));
   else if(a==='add-parameter')await parameterForm();else if(a==='edit-parameter')await parameterForm(byId(detail.parameters,id));else if(a==='delete-parameter')await remove('Используемый параметр удалить нельзя.',()=>request(`/templates/${templateId}/parameters/${id}`,'DELETE'));
   else if(a==='add-rule')await ruleForm();else if(a==='edit-rule')await ruleForm(byId(detail.rules,id));else if(a==='delete-rule')await remove('Удалить правило шаблона?',()=>request(`/templates/${templateId}/rules/${id}`,'DELETE'));
   else if(a==='add-function')await functionForm();else if(a==='edit-function')await functionForm(byId(data.functions,id));else if(a==='evaluate-function')await evaluate(byId(data.functions,id));else if(a==='delete-function')await remove('Используемую функцию удалить нельзя.',()=>request(`/functions/${id}`,'DELETE'));
   else if(a==='create-variant')await configure(id);else if(a==='edit-variant')await configure(templateId,null,byId(detail.variants,id));else if(a==='delete-variant')await remove('Вариант из заказа удалить нельзя.',()=>request(`/variants/${id}`,'DELETE'));
   else if(a==='add-ready')await readyForm();else if(a==='add-configured')await configure();else if(a==='delete-line')await remove('Убрать позицию из заказа?',()=>request(`/${orderId}/lines/${id}`,'DELETE'));
   else if(a==='edit-line'){const row=byId(detail.lines,id);if(row.variant_id){const v=byId(data.variants,row.variant_id);await configure(v.template_id,row);}else await readyForm(row);}
  }catch(e){toast.error(e.message);}
 }));
}
