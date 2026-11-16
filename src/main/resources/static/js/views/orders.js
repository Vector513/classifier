import {esc, openForm, openDialog, confirmDialog, loadingState, toast} from '../ui.js';
import {navigate} from '../router.js';
import {guidedFunctionEditor,guidedFunctionText} from './guidedFunction.js';
import {money} from '../orderMoney.js';
const base='/api/v1/orders';
const types=[['NUMBER','Число'],['INTEGER','Целое число'],['STRING','Текст'],['BOOLEAN','Да / нет'],['ENUM','Перечисление']];
const ops=[['AND','И'],['OR','ИЛИ'],['NOT','НЕ'],['IMPLIES','Если … то …'],['EQ','Равно'],['NE','Не равно'],['GT','Больше'],['GE','Больше или равно'],['LT','Меньше'],['LE','Меньше или равно'],['IN','Входит в список'],['ADD','Сложение'],['SUB','Вычитание'],['MUL','Умножение'],['DIV','Деление'],['MOD','Остаток'],['MIN','Минимум'],['MAX','Максимум'],['ABS','Модуль'],['NEG','Смена знака']];
const btn=(label,action,id='',primary=false)=>`<button type="button" class="btn ${primary?'btn--primary':'btn--secondary'}" data-action="${action}" data-id="${id}">${esc(label)}</button>`;
const orderAmount=o=>o.total_amount==null?`<span class="order-price-missing">Не рассчитана</span><small>Не рассчитано позиций: ${o.unpriced_line_count}. Сумма позиций с ценой: ${esc(money(o.priced_subtotal))}</small>`:`<strong>${esc(money(o.total_amount))}</strong>`;
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
 ['advanced','Составить своё условие'],
];
const quickLabels={
 comparison:['Первое число','Второе число'],
 range:['Проверяемое число','Нижняя граница','Верхняя граница'],
};
const functionStepTitles={
 goal:'Задача функции',
 condition:'Условие и вычисления',
 trial:'Пробная проверка',
};

function quickFunctionMode(row){
 if(row?.result_type!=='BOOLEAN'||!row.parameters.every(p=>!p.display_name&&['NUMBER','INTEGER'].includes(p.data_type??p.dataType)))return null;
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

function functionArgumentValue(value,type,label){
 if(type==='BOOLEAN')return value==='true';
 if(['NUMBER','INTEGER'].includes(type))return number(value,label);
 return value;
}

function validateExpression(e,depth=0){
 if(depth>40)throw new Error('Выражение слишком глубокое.');
 if(!e)throw new Error('Ответьте на оставшиеся вопросы, чтобы закончить условие.');
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
  if(model.kind==='FORMAL')head.append(choice('Входное значение',context.formals.map(p=>[p.code,`${p.code} — ${types.find(t=>t[0]===p.dataType)?.[1]||p.dataType}`]),model.formalCode,v=>model.formalCode=v));
  const calledFunction=model.kind==='CALL'?context.functions?.find(f=>f.id===model.functionId):null;
  if(model.kind==='CALL'){
   head.append(choice('Функция',[['','Выбрать…'],...(context.functions||[]).map(f=>[f.id,f.name])],model.functionId,v=>{model.functionId=Number(v)||null;model.args=(context.functions.find(f=>f.id===model.functionId)?.parameters||[]).map(()=>constant(0));draw();}));
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
    const box=document.createElement('div');box.className='order-argument';const controls=document.createElement('div');controls.className='order-expression-head';const label=document.createElement('strong');
    const parameter=calledFunction?.parameters[i];
    label.textContent=parameter?.display_name||parameter?.code||`Аргумент ${i+1}`;
    controls.append(label);
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
function ruleTrialParameters(expression,parameters){
 const referenced=new Set();
 function visit(node){
  if(node.kind==='PARAMETER')referenced.add(node.parameterCode);
  (node.args||[]).forEach(visit);
 }
 visit(expression);
 return parameters.filter(p=>referenced.has(p.code)).map(p=>({
  code:p.code,
  displayName:p.unit?`${p.name} (${p.unit})`:p.name,
  dataType:p.data_type,
  options:p.data_type==='ENUM'?p.options:undefined,
 }));
}

function expressionDialog({title,row,context,isFunction,onTrial,onSave,onSaved}){
  const existingMode=isFunction?quickFunctionMode(row):null;
  let mode=isFunction?(existingMode||(row?'advanced':'comparison')):'advanced';
  let step=0;
  const drafts={
   comparison:quickFunctionDraft('comparison'),
   range:quickFunctionDraft('range'),
  };
  if(row||!isFunction){
   drafts[mode]={
    expression:clone(row?.expression??null),
    parameters:(row?.parameters||[]).map(p=>({code:p.code,dataType:p.data_type??p.dataType,displayName:p.display_name})),
    resultType:row?.result_type||'BOOLEAN',
   };
  }
  const templateParameters=isFunction?undefined:context.parameters;
  const code=row?.code??`${isFunction?'FN':'RULE'}_${Date.now()}_${crypto.getRandomValues(new Uint32Array(1))[0].toString(36)}`;
  return openDialog({title,wide:true,build(api){
   const modes=row?functionModes.filter(([value])=>value===existingMode||value==='advanced'):functionModes;
   const options=(items,selected)=>items.map(([value,label])=>
    `<option value="${value}" ${value===selected?'selected':''}>${esc(label)}</option>`
   ).join('');
   const codeField=`<label class="order-control">Код
    <input class="input" name="code" maxlength="100" pattern="[A-Za-z_][A-Za-z0-9_]*" required value="${esc(code)}">
   </label>`;
   api.body.innerHTML=`
    <form class="orders-editor-form">
     <header class="order-wizard-heading"><p data-step-count></p><h3 data-step-title tabindex="-1"></h3></header>
     <div class="order-function-body" data-function-goal>
     <label class="order-control">Название
      <input class="input" name="name" maxlength="255" required value="${esc(row?.name||'')}">
     </label>
     ${isFunction&&modes.length>1?`<div class="order-control">
      <label for="order-function-mode">Что должна делать функция?</label>
      <select class="select" id="order-function-mode" name="functionMode">${options(modes,mode)}</select>
     </div>`:''}
     ${isFunction?'<p class="orders-empty" data-mode-help></p>':''}
     ${isFunction?'':`<label class="order-control">Что показать, если условие не выполнено?
      <input class="input" name="errorMessage" maxlength="10000" required placeholder="Например, для двух мониторов выберите зарядку от 100 Вт" value="${esc(row?.error_message||'')}">
     </label>`}
     <details class="order-function-code">
      <summary>${isFunction?'Код функции':'Код правила'}</summary>
      ${codeField}
     </details>
     </div>
     <div class="order-function-body" data-function-body></div>
     <section class="order-function-body" data-function-trial hidden></section>
     <p class="orders-error" role="alert" data-editor-error hidden></p>
    </form>`;
   const form=api.body.querySelector('form');
   const error=form.querySelector('[data-editor-error]');
   const goal=form.querySelector('[data-function-goal]');
   const body=form.querySelector('[data-function-body]');
   const trial=form.querySelector('[data-function-trial]');
   const trialValues=new Map();
   const codeDetails=form.querySelector('.order-function-code');
   form.elements.code.addEventListener('invalid',()=>{codeDetails.open=true;});
   let detailed=false;
   let guidedReady=true;
   const modeSelect=form.elements.namedItem('functionMode');
   modeSelect?.addEventListener('change',()=>{
    if(!drafts[modeSelect.value])drafts[modeSelect.value]=row?clone(drafts[mode]):{
     expression:null,parameters:[],resultType:'BOOLEAN',
    };
    mode=modeSelect.value;
    error.hidden=true;
    draw();
    showStep();
   });
   function draw(){
    const draft=drafts[mode];
    guidedReady=true;
    if(mode!=='advanced'){
     body.innerHTML=`<section class="order-function-preset" data-function-condition>
      ${mode==='comparison'?`<div class="order-control">
       <label for="order-function-comparison">Как сравнить числа?</label>
       <select class="select" id="order-function-comparison" name="comparison">${options(comparisons,draft.expression.operator)}</select>
      </div>`:''}
      <p class="order-function-preview" data-quick-preview aria-live="polite"></p>
      ${mode==='range'?'<p class="orders-empty">Границы тоже считаются допустимыми. Например, 16 входит в диапазон от 16 до 64.</p>':''}
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
    if(!detailed){
     body.innerHTML='<div class="order-function-body" data-function-condition></div>';
     const condition=body.firstElementChild;
     const technical=document.createElement('button');
     technical.type='button';
     technical.className='btn btn--secondary';
     technical.textContent='Подробный редактор';
     technical.onclick=()=>{detailed=true;draw();showStep();};
     const guided=guidedFunctionEditor(draft,context.functions.filter(f=>!isFunction||f.id!==row?.id),ready=>{
      guidedReady=ready;
      technical.hidden=!ready;
      if(!condition.hidden&&condition.isConnected)updateNext();
     },templateParameters);
     condition.append(guided,technical);
     return;
    }
    body.innerHTML=`${isFunction?`<div class="order-function-body" data-function-inputs>
     <p class="orders-empty">Добавьте по одному входу на каждое значение. Например, <code>power</code> для мощности и <code>limit</code> для порога. Для проверки выберите результат «Да / нет».</p>
     <section data-signature></section>
     <label class="order-control">Что вернёт функция?
      <select class="select" name="resultType">${options(types,draft.resultType)}</select>
     </label>
    </div>`:''}
    <div class="order-function-body" data-function-condition>
     ${isFunction?'<p class="orders-empty">Выберите операцию, например «Больше». В её полях укажите, что сравнивать: входное значение, заданное число или результат другой операции.</p>':''}
     <div data-expression></div>
    </div>`;
    const drawExpression=()=>body.querySelector('[data-expression]').replaceChildren(
     expressionEditor(draft.expression,{...context,formals:draft.parameters})
    );
    const basic=document.createElement('button');
    basic.type='button';
    basic.className='btn btn--secondary';
    basic.textContent='Вернуться к пошаговому редактору';
    basic.onclick=()=>{detailed=false;draw();showStep();};
    body.querySelector('[data-function-condition]').prepend(basic);
    if(isFunction){
     form.elements.resultType.addEventListener('change',event=>{draft.resultType=event.target.value;});
     const area=body.querySelector('[data-signature]');
     draft.parameters.forEach((parameter,index)=>{
      const line=document.createElement('div');
      line.className='order-signature-line';
      line.innerHTML=`<label class="order-control">Код входа ${index+1}
       <input class="input" value="${esc(parameter.code)}" aria-label="Код аргумента ${index+1}" pattern="[A-Za-z_][A-Za-z0-9_]*" required>
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
      line.querySelector('button').onclick=()=>{draft.parameters.splice(index,1);draw();showStep();};
      area.append(line);
     });
     const add=document.createElement('button');
     add.type='button';
     add.className='btn btn--secondary';
     add.textContent='Добавить входное значение';
     add.onclick=()=>{
      let index=draft.parameters.length+1;
      while(draft.parameters.some(p=>p.code===`arg${index}`))index++;
      draft.parameters.push({code:`arg${index}`,dataType:'NUMBER'});
      draw();
      showStep();
     };
     area.append(add);
    }
    drawExpression();
   }
   const steps=['goal','condition','trial'];
   function parametersToSave(draft){
    if(row||detailed||mode!=='advanced')return draft.parameters;
    const used=new Set();
    function visit(node){
     if(!node)return;
     if(node.kind==='FORMAL')used.add(node.formalCode);
     node.args.forEach(visit);
    }
    visit(draft.expression);
    return draft.parameters.filter(parameter=>used.has(parameter.code));
   }
   function dataToSave(){
    const draft=drafts[mode];
    const name=form.elements.name.value.trim();
    if(!name)throw new Error(isFunction?'Укажите название функции.':'Укажите название правила.');
    const data={
     code:form.elements.code.value.trim(),name,
     expression:validateExpression(draft.expression),
    };
    if(isFunction)Object.assign(data,{resultType:draft.resultType,parameters:parametersToSave(draft)});
    else {
     data.errorMessage=form.elements.errorMessage.value.trim();
     if(!data.errorMessage)throw new Error('Напишите, что предложить покупателю, если условие не выполнено.');
    }
    return data;
   }
   function drawTrial(){
    const draft=drafts[mode];
    const summary=mode==='advanced'?guidedFunctionText(draft.expression,draft.parameters,context.functions,templateParameters):
     quickFunctionSummary(mode,draft.expression.operator);
    trial.innerHTML=`
     <div class="order-function-preset">
      <p class="order-function-preview">${esc(summary)}</p>
     </div>
     <p class="orders-empty">${isFunction?'Подставьте пример и посмотрите, что вернёт функция. Пробная проверка не сохраняет её в библиотеке.':'Подставьте пример и посмотрите, пройдёт ли выбранная комплектация это правило. Можно попробовать до сохранения.'}</p>
     <div class="order-trial-values" data-trial-values></div>
     <div><button class="btn btn--secondary" type="button" data-run-trial>Проверить на примере</button></div>
     <p class="order-trial-result" role="status" data-trial-result hidden></p>`;
    const test=trial.querySelector('[data-run-trial]');
    const result=trial.querySelector('[data-trial-result]');
    let revision=0;
    const examples=mode==='comparison'?['650','450']:['32','16','64'];
    const inputs=(isFunction?parametersToSave(draft):ruleTrialParameters(draft.expression,templateParameters)).map((parameter,index)=>{
     const {code,dataType}=parameter;
     const label=parameter.displayName||quickLabels[mode]?.[index]||code;
     const key=`${mode}:${code}:${dataType}`;
     const wrap=document.createElement('label');
     wrap.className='order-control';
     wrap.textContent=label;
     const input=document.createElement(dataType==='BOOLEAN'||parameter.options?'select':'input');
     input.setAttribute('aria-label',label);
     if(dataType==='BOOLEAN'){
      input.className='select';
      input.add(new Option('Выберите значение',''));
      input.add(new Option('Да','true'));
      input.add(new Option('Нет','false'));
     }else if(parameter.options){
      input.className='select';
      input.add(new Option('Выберите значение',''));
      parameter.options.forEach(option=>input.add(new Option(option.name,option.code)));
     }else{
      input.className='input';
      if(dataType==='NUMBER'||dataType==='INTEGER')input.inputMode='decimal';
     }
     input.value=trialValues.get(key)??(mode==='advanced'?'':examples[index]);
     input.addEventListener('input',()=>{
      trialValues.set(key,input.value);
      revision++;
      result.hidden=true;
      test.disabled=false;
     });
     wrap.append(input);
     trial.querySelector('[data-trial-values]').append(wrap);
     return {input,label,dataType,code};
    });
    test.addEventListener('click',async()=>{
     const run=++revision;
     test.disabled=true;
     result.hidden=false;
     result.className='order-trial-result';
     result.textContent='Проверяем…';
     try{
      const readInput=({input,label,dataType})=>{
       if(dataType==='BOOLEAN'&&!input.value)throw new Error(`Выберите «Да» или «Нет» для «${label}».`);
       const value=functionArgumentValue(input.value,dataType,label);
       if(dataType==='INTEGER'&&!Number.isSafeInteger(value))throw new Error(`Для «${label}» введите целое число.`);
       return value;
      };
      const data=dataToSave();
      const value=await onTrial(isFunction?{function:data,arguments:inputs.map(readInput)}:{
       expression:data.expression,
       values:Object.fromEntries(inputs.filter(({input})=>input.value!=='').map(entry=>[entry.code,readInput(entry)])),
      });
      if(run!==revision)return;
      result.classList.add(!isFunction&&!value?'orders-error':'orders-success');
      result.textContent=isFunction?`Результат — ${typeof value==='boolean'?(value?'Да':'Нет'):value}`:
       value?'Условие выполнено. Комплектация проходит это правило.':`Условие не выполнено. ${data.errorMessage}`;
     }catch(e){
      if(run!==revision)return;
      result.classList.add('orders-error');
      result.textContent=e.message;
     }finally{
      if(run===revision)test.disabled=false;
     }
    });
   }
   function showStep(focus=false){
    const current=steps[step];
    form.querySelector('[data-step-count]').textContent=`Шаг ${step+1} из ${steps.length}`;
    const heading=form.querySelector('[data-step-title]');
    heading.textContent=isFunction?functionStepTitles[current]:{goal:'Название и сообщение',condition:'Условие совместимости',trial:'Проверка на примере'}[current];
    goal.hidden=current!=='goal';
    body.hidden=current!=='condition';
    trial.hidden=current!=='trial';
    back.hidden=step===0;
    updateNext();
    if(current==='trial')drawTrial();
    if(focus){
     form.closest('.modal-overlay').scrollTop=0;
     heading.focus();
    }
   }
   function updateNext(){
    const current=steps[step];
    next.querySelector('span').textContent=current==='trial'?'Сохранить':'Далее';
    next.disabled=current==='condition'&&!guidedReady;
   }
   if(isFunction){
    const help=form.querySelector('[data-mode-help]');
    const descriptions={
     comparison:'Например, проверить, хватает ли мощности блока питания.',
     range:'Например, проверить, входит ли длина изделия в допустимый диапазон.',
     advanced:'Отвечайте на вопросы по очереди. Например, сначала выберите, что сравнить, а затем укажите, откуда взять каждое значение.',
    };
    const updateHelp=()=>{help.textContent=descriptions[mode];};
    modeSelect?.addEventListener('change',updateHelp);
    updateHelp();
   }
   api.addButton({label:'Отмена',onClick(){api.close();}});
   const back=api.addButton({label:'Назад',id:'function-back',onClick(){
    step--;
    error.hidden=true;
    showStep(true);
   }});
   const next=api.addButton({label:'Сохранить',variant:'btn--primary',async onClick(_,button){
    error.hidden=true;
    try{
     const current=steps[step];
     if(current!=='trial'){
      const panel=current==='goal'?goal:body;
      if(![...panel.querySelectorAll('input,select,textarea')].every(input=>input.reportValidity()))return;
      if(current==='goal'&&!form.elements.name.value.trim())throw new Error(isFunction?'Укажите название функции.':'Укажите название правила.');
      if(current==='condition')dataToSave();
      step++;
      showStep(true);
      return;
     }
     button.disabled=true;
     back.disabled=true;
     await onSave(dataToSave());
     api.close();
     await onSaved?.();
    }catch(e){
     error.textContent=e.message;
     error.hidden=false;
    }finally{
     updateNext();
     back.disabled=false;
    }
   }});
   form.addEventListener('submit',event=>{
    event.preventDefault();
    if(steps[step]==='trial')trial.querySelector('[data-run-trial]').click();
    else next.click();
   });
   draw();
   showStep();
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
 function ruleForm(row){
  const path=`/templates/${templateId}/rules`;
  return expressionDialog({
   title:row?'Изменить правило':'Новое правило совместимости',row,
   context:{parameters:detail.parameters,functions:data.functions},
   onTrial:example=>request(path+'/trial','POST',example),
   onSave:rule=>request(path+idQuery(row),'POST',rule),
   onSaved:refresh,
  });
 }
 function functionForm(row){
  return expressionDialog({
   title:row?'Изменить функцию':'Новая функция ограничения',row,isFunction:true,
   context:{functions:data.functions},
   onTrial:example=>request('/functions/trial'+idQuery(row),'POST',example),
   onSave:fn=>request('/functions'+idQuery(row),'POST',fn),
   onSaved:refresh,
  });
 }
 async function componentForm(row){
  const conditions=detail.parameters.map(p=>{
   const value=row?.when_values?.[p.code];const name=`condition_${p.id}`,label=`Включать, если «${p.name}» равно`;
   if(p.data_type==='ENUM')return select(name,label,p.options.map(o=>[o.code,o.name]),value??'',false);
   if(p.data_type==='BOOLEAN')return select(name,label,[['true','Да'],['false','Нет']],value==null?'':String(value),false);
   return field(name,label,value??'',false);
  });
  return form(row?'Изменить компонент':'Добавить компонент',[
   select('componentId','Компонент',pairs(data.catalog.filter(n=>n.node_kind!=='GROUP'&&n.id!==detail.template.product_id),n=>`${n.name} · ${n.code}`),row?.component_id),
   {...field('quantity','Количество или множитель',row?.quantity??1),hint:'Количество на одно изделие. При выборе параметра умножается на его значение; для модулей 8 ГБ множитель равен 0,125. Ноль исключает компонент для выбранного условия.'},
   select('quantityParameterId','Умножать на параметр',pairs(detail.parameters.filter(p=>['NUMBER','INTEGER'].includes(p.data_type))),row?.quantity_parameter_id,false),
   field('selectionGroup','Группа компонента',row?.selection_group,false),
   select('requiredGroup','Группа обязательна',[['true','Да'],['false','Нет']],String(row?.required_group??false)),...conditions
  ],v=>{
   const whenValues={};detail.parameters.forEach(p=>{const raw=v[`condition_${p.id}`];if(raw!==''&&raw!=null)whenValues[p.code]=p.data_type==='BOOLEAN'?raw==='true':['NUMBER','INTEGER'].includes(p.data_type)?number(raw,p.name):raw;});
   return request(`/templates/${templateId}/components${idQuery(row)}`,'POST',{componentId:Number(v.componentId),quantity:number(v.quantity,'Количество компонента'),quantityParameterId:v.quantityParameterId?Number(v.quantityParameterId):null,selectionGroup:v.selectionGroup,requiredGroup:v.requiredGroup==='true',whenValues});
  },{subtitle:'Пустое условие допускает любое значение параметра. Заполненные условия должны выполняться одновременно. Стоимость вложенной сборки рассчитывается по её утверждённому составу.'});
 }
 function lineCost(row){return openDialog({title:`Расчёт стоимости · ${row.name}`,wide:true,build(api){
  api.body.classList.add('order-cost-body');
  const resourceQuantity=c=>`${String(c.quantity??'—').replace(/(\.\d*?)0+$/,'$1').replace(/\.$/,'').replace('.',',')} ${['PCS','BOM-PCS'].includes(c.unit)?'шт.':c.unit||''}`;
  api.body.innerHTML=`<p>Стоимость за единицу: <strong>${esc(money(row.unit_price))}</strong>. Количество в заказе: ${row.quantity}. Сумма позиции: <strong>${esc(money(row.line_total))}</strong>.</p>
   ${(row.cost_issues||[]).length?`<div class="orders-error">${row.cost_issues.map(esc).join('<br>')}</div>`:''}
   ${table(['Компонент','На одно изделие','Цена ресурса','Источник','Стоимость'],(row.cost_components||[]).map(c=>[`${esc(c.name)}<br>${esc(c.code)}`,esc(resourceQuantity(c)),`<span class="order-money">${esc(money(c.unit_price))}</span>`,c.price_source==='PURCHASE_PRICE'?'Закупочная цена':'Цена каталога',`<span class="order-money">${esc(money(c.amount))}</span>`]))}
   <p class="orders-empty">Вложенные сборки раскрываются до материалов и деталей. Их собственная цена повторно не прибавляется. При отсутствии закупочной цены используется цена ресурса из каталога. Итог обновляется по текущим ценам компонентов.</p>`;
  api.addButton({label:'Закрыть',onClick:()=>api.close()});
 }});}
 async function readyForm(row){return form(row?'Изменить позицию':'Добавить готовое изделие',[select('productId','Готовое изделие',pairs(ready,n=>`${n.name} · ${n.code}`),row?.product_id),field('quantity','Количество',row?.quantity||1),field('note','Примечание',row?.note,false)],v=>request(`/${orderId}/lines${idQuery(row)}`,'POST',{productId:Number(v.productId),quantity:integer(v.quantity),note:v.note}));}
 async function addConfiguredLine(){
  if(!data.templates.length)throw new Error('Сначала создайте шаблон в разделе «Конструктор изделий».');
  const newVariant='new';
  const quantity=field('quantity','Количество',1);
  const result=await openForm({
   title:'Добавить исполнение по шаблону',
   wide:true,
   submitLabel:'Добавить в заказ',
   fields:[
    select('templateId','Шаблон',pairs(data.templates)),
    select('variantId','Вариант исполнения',[]),
    quantity,
    field('note','Примечание','',false),
   ],
   async onSubmit(values){
    if(values.variantId===newVariant)return {newTemplateId:Number(values.templateId)};
    return request(`/${orderId}/lines`,'POST',{
     variantId:Number(values.variantId),
     quantity:integer(values.quantity),
     note:values.note,
    });
   },
   onMount(form){
    const templateSelect=form.elements.templateId;
    const variantSelect=form.elements.variantId;
    const orderFields=['quantity','note'].map(name=>form.querySelector(`[data-field="${name}"]`));
    function toggleOrderFields(){
     const value=variantSelect.value;
     const useSavedVariant=Boolean(value)&&value!==newVariant;
     quantity.required=useSavedVariant;
     orderFields.forEach(element=>{element.hidden=!useSavedVariant;});
     const submitLabel=form.closest('.modal').querySelector('[data-act="submit"] span');
     if(submitLabel)submitLabel.textContent=value===newVariant?'Продолжить':'Добавить в заказ';
    }
    function updateVariants(){
     const templateId=Number(templateSelect.value);
     const saved=templateId?data.variants.filter(variant=>variant.template_id===templateId):[];
     const placeholder=templateId?'Выберите вариант':'Сначала выберите шаблон';
     variantSelect.replaceChildren(new Option(placeholder,''));
     variantSelect.disabled=!templateId;
     if(templateId){
      saved.forEach(variant=>variantSelect.add(new Option(`${variant.name} · ${variant.code}`,variant.id)));
      variantSelect.add(new Option('Создать новое исполнение',newVariant));
      if(!saved.length)variantSelect.value=newVariant;
     }
     toggleOrderFields();
    }
    templateSelect.addEventListener('change',updateVariants);
    variantSelect.addEventListener('change',toggleOrderFields);
    updateVariants();
   },
  });
  if(result?.newTemplateId)await configure(result.newTemplateId);
  else if(result)await refresh();
 }
 async function configure(tplId,row,existingVariant){
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
   if(p.data_type==='BOOLEAN')return select(p.code,p.display_name||p.code,[['true','Да'],['false','Нет']],'true');
   const label=p.display_name||labels?.[index]||`${p.code} (${types.find(([type])=>type===p.data_type)?.[1]})`;
   return field(p.code,label);
  });
  return openForm({
   title:`Проверить функцию «${row.name}»`,
   subtitle:mode?quickFunctionSummary(mode,row.expression.operator):undefined,
   fields,
   async onSubmit(values){
    const args=row.parameters.map((p,index)=>
     functionArgumentValue(values[p.code],p.data_type,p.display_name||labels?.[index]||p.code)
    );
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
  body=`<section class="card order-panel"><div class="order-section-head"><div><h2>${esc(detail.template.name)}</h2><p class="orders-empty">${esc(detail.template.code)}</p></div><div class="order-actions">${btn('Изменить шаблон','edit-template',templateId)}${btn('Создать вариант','create-variant',templateId,true)}</div></div><h3>Параметры конфигурации</h3>${btn('Добавить параметр','add-parameter')}${table(['Код','Параметр','Тип','Границы / варианты','Действия'],detail.parameters.map(p=>[esc(p.code),`${esc(p.name)} ${p.required?'<span class="badge">обязательный</span>':''}`,esc(types.find(t=>t[0]===p.data_type)?.[1]),p.data_type==='ENUM'?p.options.map(o=>esc(o.name)).join(', '):`${fieldRow(p.min_value,p.unit)} … ${fieldRow(p.max_value,p.unit)}`,actions('edit-parameter','delete-parameter',p.id)]))}<h3>Состав для расчёта стоимости</h3>${btn('Добавить компонент','add-component')}<p class="orders-empty">Количество компонента задаётся на одно изделие. Для вариантов состав выбирается по их параметрам.</p>${table(['Группа','Компонент','Количество','Условие включения','Действия'],(detail.components||[]).map(c=>[`${esc(c.selection_group||'—')}${c.required_group?' · обязательная':''}`,`${esc(c.component_name)}<br>${esc(c.component_code)}`,`${esc(c.quantity)}${c.quantity_code?' × '+esc(c.quantity_code):''}`,Object.entries(c.when_values).map(([k,v])=>`${esc(k)} = ${esc(String(v))}`).join(', ')||'Всегда',actions('edit-component','delete-component',c.id)]))}<h3>Правила совместимости</h3>${btn('Добавить правило','add-rule')}${table(['Правило','Условие','Сообщение','Действия'],detail.rules.map(r=>[esc(r.name),`<span class="order-formula">${esc(guidedFunctionText(r.expression,[],data.functions,detail.parameters))}</span>`,esc(r.error_message),actions('edit-rule','delete-rule',r.id)]))}<h3>Варианты исполнения</h3>${table(['Код','Исполнение','Значения','Действия'],detail.variants.map(v=>[esc(v.code),esc(v.name),Object.entries(v.values).map(([k,val])=>`${esc(k)}: ${esc(String(val))}`).join('<br>'),actions('edit-variant','delete-variant',v.id)]))}</section>`;
 }else if(orderId){
  const o=detail.order;body=`<section class="card order-panel"><div class="order-section-head"><div><h2>Заказ ${esc(o.order_number)}</h2><p>${esc(o.customer.name)} · ${esc(o.order_date)}${o.requested_date?' · срок '+esc(o.requested_date):''}</p></div><div class="order-actions">${btn('Изменить заказ','edit-order',orderId)}<a class="btn btn--secondary" href="${base}/${orderId}/document" target="_blank" rel="noopener">Печать заказа</a></div></div><div class="order-actions">${btn('Добавить готовое изделие','add-ready')}${btn('Добавить исполнение по шаблону','add-configured','',true)}</div>${table(['№','Изделие / исполнение','Параметры','Количество','Цена за ед.','Сумма','Примечание','Действия'],detail.lines.map(l=>[String(l.line_number),`<strong>${esc(l.name)}</strong><br>${esc(l.code)}${l.variant_id?'<br><span class="badge">вариант исполнения</span>':''}`,l.parameters.map(p=>`${esc(p.name)}: ${esc(String(p.value))} ${esc(p.unit||'')}`).join('<br>')||'—',String(l.quantity),`<span class="order-money">${esc(money(l.unit_price))}</span>`,`<span class="order-money">${esc(money(l.line_total))}</span>`,esc(l.note||''),`<div class="order-actions">${btn('Расчёт','line-cost',l.id)}${btn('Изменить','edit-line',l.id)}${btn('Удалить','delete-line',l.id)}</div>`]))}<div class="order-total"><span>Итого по заказу</span><div>${orderAmount(o)}</div></div></section>`;
 }else if(section==='templates')body=`<section class="card order-panel"><div class="order-section-head"><h2>Шаблоны изделий</h2>${btn('Новый шаблон','add-template','',true)}</div><p class="orders-empty">Задай параметры изделия и правила допустимых сочетаний. Из шаблона создаются проверенные исполнения.</p>${table(['Код','Шаблон','Действия'],data.templates.map(t=>[esc(t.code),`<a href="#/orders/templates/${t.id}">${esc(t.name)}</a>`,actions('edit-template','delete-template',t.id)]))}</section>`;
 else if(section==='functions'){
  const rows=data.functions.map(f=>{
   const mode=quickFunctionMode(f);
   const summary=mode?quickFunctionSummary(mode,f.expression.operator):guidedFunctionText(f.expression,f.parameters,data.functions);
   return [
    `${esc(f.name)}<br><span class="order-formula">${esc(summary)}</span>`,
    f.parameters.map((p,index)=>p.display_name||quickLabels[mode]?.[index]||p.code).map(esc).join(', '),
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
 else body=`<section class="card order-panel"><div class="order-section-head"><h2>Заказы</h2>${btn('Создать заказ','add-order','',true)}</div><p class="orders-empty">Включай готовые изделия или создавай исполнение с проверкой параметров по шаблону.</p>${table(['Номер','Дата','Заказчик','Позиций','Сумма','Действия'],data.orders.map(o=>[`<a href="#/orders/${o.id}">${esc(o.order_number)}</a>`,esc(o.order_date),esc(o.customer_name),String(o.line_count),orderAmount(o),actions('edit-order','delete-order',o.id)]))}</section>`;
 container.innerHTML=`<div class="page orders-page"><div class="page-head"><div class="page-head__text"><h1>Заказы и конфигурация</h1><p>Изделия с вариантами исполнения</p></div></div><nav class="order-tabs" aria-label="Работа с заказами">${nav.map(([key,name,path])=>`<a href="#${path}" class="${tab===key?'is-selected':''}" ${tab===key?'aria-current="page"':''}>${name}</a>`).join('')}</nav>${body}</div>`;
 const byId=(rows,id)=>rows.find(r=>r.id===Number(id));
 container.querySelectorAll('[data-action]').forEach(button=>button.addEventListener('click',async()=>{
  const a=button.dataset.action,id=Number(button.dataset.id);try{
   if(a==='add-customer')await customerForm();else if(a==='edit-customer')await customerForm(byId(data.customers,id));else if(a==='delete-customer')await remove('Заказчика с заказами удалить нельзя.',()=>request(`/customers/${id}`,'DELETE'));
   else if(a==='add-order')await orderForm();else if(a==='edit-order')await orderForm(byId(data.orders,id));else if(a==='delete-order')await remove('Заказ и его позиции будут удалены. Исполнения сохранятся.',()=>request(`/${id}`,'DELETE'));
   else if(a==='add-template')await templateForm();else if(a==='edit-template')await templateForm(byId(data.templates,id));else if(a==='delete-template')await remove('Шаблон с созданными вариантами удалить нельзя.',()=>request(`/templates/${id}`,'DELETE'));
   else if(a==='add-parameter')await parameterForm();else if(a==='edit-parameter')await parameterForm(byId(detail.parameters,id));else if(a==='delete-parameter')await remove('Используемый параметр удалить нельзя.',()=>request(`/templates/${templateId}/parameters/${id}`,'DELETE'));
   else if(a==='add-component')await componentForm();else if(a==='edit-component')await componentForm(byId(detail.components,id));else if(a==='delete-component')await remove('Убрать компонент из расчётного состава шаблона?',()=>request(`/templates/${templateId}/components/${id}`,'DELETE'));
   else if(a==='add-rule')await ruleForm();else if(a==='edit-rule')await ruleForm(byId(detail.rules,id));else if(a==='delete-rule')await remove('Удалить правило шаблона?',()=>request(`/templates/${templateId}/rules/${id}`,'DELETE'));
   else if(a==='add-function')await functionForm();else if(a==='edit-function')await functionForm(byId(data.functions,id));else if(a==='evaluate-function')await evaluate(byId(data.functions,id));else if(a==='delete-function')await remove('Используемую функцию удалить нельзя.',()=>request(`/functions/${id}`,'DELETE'));
   else if(a==='create-variant')await configure(id);else if(a==='edit-variant')await configure(templateId,null,byId(detail.variants,id));else if(a==='delete-variant')await remove('Вариант из заказа удалить нельзя.',()=>request(`/variants/${id}`,'DELETE'));
   else if(a==='line-cost')await lineCost(byId(detail.lines,id));else if(a==='add-ready')await readyForm();else if(a==='add-configured')await addConfiguredLine();else if(a==='delete-line')await remove('Убрать позицию из заказа?',()=>request(`/${orderId}/lines/${id}`,'DELETE'));
   else if(a==='edit-line'){const row=byId(detail.lines,id);if(row.variant_id){const v=byId(data.variants,row.variant_id);await configure(v.template_id,row);}else await readyForm(row);}
  }catch(e){toast.error(e.message);}
 }));
}
