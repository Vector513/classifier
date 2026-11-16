import {esc} from '../ui.js';

const groups=[
 {id:'compare',label:'Сравнить значения',result:'BOOLEAN',options:[
  ['GE','Не меньше','≥'],['GT','Больше','>'],['LE','Не больше','≤'],
  ['LT','Меньше','<'],['EQ','Равно','='],['NE','Не равно','≠'],
 ]},
 {id:'logic',label:'Объединить условия',result:'BOOLEAN',options:[
  ['AND','Выполнены все условия','и'],['OR','Выполнено хотя бы одно условие','или'],
  ['IMPLIES','Если выполнено первое, проверить второе',''],['NOT','Проверить, что условие не выполнено',''],
 ]},
 {id:'math',label:'Посчитать число',result:'NUMBER',options:[
  ['ADD','Сложить','+'],['SUB','Вычесть','−'],['MUL','Умножить','×'],['DIV','Разделить','÷'],
  ['MOD','Остаток от деления',''],['MIN','Взять наименьшее',''],['MAX','Взять наибольшее',''],
  ['ABS','Взять модуль',''],['NEG','Сменить знак',''],
 ]},
 {id:'list',label:'Проверить по списку',result:'BOOLEAN',options:[['IN','Входит в список','']]},
];
const unary=new Set(['NOT','ABS','NEG']);
const prompts={
 IMPLIES:['Когда проверять?','Что должно выполниться?'],
 IN:['Что ищем в списке?','Какие значения допустимы?'],
 SUB:['Из чего вычесть?','Что вычесть?'],
 MUL:['Что умножаем?','На что умножаем?'],
 DIV:['Что делим?','На что делим?'],
 MOD:['Что делим?','На что делим?'],
};
const inputTypes=[['NUMBER','Число'],['INTEGER','Целое число'],['STRING','Текст'],['BOOLEAN','Да или нет']];
const groupOf=code=>groups.find(group=>group.options.some(([value])=>value===code));
const numeric=type=>type==='NUMBER'||type==='INTEGER';
const textual=type=>type==='STRING'||type==='ENUM';
const compatible=(type,expected)=>!expected||expected===type||
 (numeric(type)&&numeric(expected))||(textual(type)&&textual(expected))||
 (expected==='ORDERED'&&(numeric(type)||textual(type)));

function parameterCaption(parameter){
 return parameter?.displayName||parameter?.display_name||parameter?.code;
}

const templateCaption=parameter=>parameter.unit?`${parameter.name} (${parameter.unit})`:parameter.name;
const templateParameter=(node,parameters)=>node?.kind==='PARAMETER'
 ?parameters?.find(parameter=>parameter.code===node.parameterCode):undefined;

export function guidedFunctionText(node,parameters,functions,templateParameters){
 if(!node)return '…';
 if(node.kind==='FORMAL')return parameterCaption(parameters.find(p=>p.code===node.formalCode))||node.formalCode;
 if(node.kind==='PARAMETER'){
  const parameter=templateParameter(node,templateParameters);
  return parameter?templateCaption(parameter):node.parameterCode;
 }
 if(node.kind==='CONST'){
  if(typeof node.value==='boolean')return node.value?'Да':'Нет';
  return typeof node.value==='string'?`«${node.value}»`:JSON.stringify(node.value);
 }
 const args=node.args.map((child,index)=>{
  const choices=templateParameter(node.args[index===0?1:0],templateParameters)?.options;
  if(child?.kind==='CONST'&&choices?.length){
   const label=value=>choices.find(option=>option.code===value)?.name??value;
   return Array.isArray(child.value)?child.value.map(label).join(', '):`«${label(child.value)}»`;
  }
  return guidedFunctionText(child,parameters,functions,templateParameters);
 });
 if(node.kind==='CALL')return `${functions.find(f=>f.id===node.functionId)?.name||'Функция'}(${args.join(', ')})`;
 if(node.operator==='IMPLIES')return `Если ${args[0]}, то ${args[1]}`;
 if(node.operator==='IN')return `${args[0]} входит в ${args.slice(1).join(', ')}`;
 const info=groupOf(node.operator)?.options.find(([code])=>code===node.operator);
 if(info?.[2])return args.map((arg,i)=>node.args[i]?.kind==='OPERATOR'?`(${arg})`:arg).join(` ${info[2]} `);
 return `${info?.[1]||node.operator||node.parameterCode||node.kind}(${args.join(', ')})`;
}

function resultOf(node,parameters,functions,templateParameters){
 switch(node?.kind){
  case 'FORMAL': return parameters.find(parameter=>parameter.code===node.formalCode)?.dataType;
  case 'PARAMETER': return templateParameter(node,templateParameters)?.data_type;
  case 'CALL': return functions.find(f=>f.id===node.functionId)?.result_type;
  case 'OPERATOR': return groupOf(node.operator)?.result;
  case 'CONST':
   if(Array.isArray(node.value))return 'LIST';
   return {number:'NUMBER',string:'STRING',boolean:'BOOLEAN'}[typeof node.value];
 }
}

function missingPath(node,path=[]){
 if(!node)return path;
 for(const [i,child] of node.args.entries()){
  const missing=missingPath(child,[...path,i]);
  if(missing)return missing;
 }
 return null;
}

function question(parent,index,functions,isRule){
 if(!parent)return isRule?'Что должно быть верно для этой комплектации?':'Что должна сделать функция?';
 if(parent.kind==='CALL'){
  const name=parameterCaption(functions.find(f=>f.id===parent.functionId)?.parameters[index]);
  return name?`Что подставить в «${name}»?`:'Что подставить в эту функцию?';
 }
 const known=prompts[parent.operator]?.[index];
 if(known)return known;
 const group=groupOf(parent.operator)?.id;
 if(group==='logic')return index===0?'Какое условие проверить первым?':'Какое ещё условие проверить?';
 if(group==='compare')return index===0?'Что сравниваем?':'С чем сравниваем?';
 return index===0?'Какое число взять?':'Какое число взять дальше?';
}

function dataTypeFor(parent,index,parameters,functions,templateParameters){
 if(!parent)return templateParameters?'BOOLEAN':undefined;
 if(parent.kind==='CALL')return functions.find(f=>f.id===parent.functionId)?.parameters[index]?.data_type;
 const group=groupOf(parent.operator)?.id;
 if(group==='math')return 'NUMBER';
 if(group==='logic')return 'BOOLEAN';
 if(parent.operator==='IN'&&index===1&&parent.args.length===2)return 'LIST';
 const peer=parent.args[index===0?1:0];
 const peerType=resultOf(peer,parameters,functions,templateParameters);
 return (peerType==='LIST'?undefined:peerType)||
  (group==='compare'&&!['EQ','NE'].includes(parent.operator)?'ORDERED':undefined);
}

const options=(items,selected)=>items.map(([value,label])=>
 `<option value="${esc(value)}"${String(value)===String(selected)?' selected':''}>${esc(label)}</option>`
).join('');
const choose=(value,label)=>`<button type="button" class="btn btn--secondary" data-choose="${esc(value)}">${esc(label)}</button>`;
const valueControl=(type,value)=>type==='BOOLEAN'
 ?`<select class="select" data-value>${options([['true','Да'],['false','Нет']],value)}</select>`
 :`<input class="input" data-value value="${esc(value??'')}" ${numeric(type)?'inputmode="decimal"':''}>`;

function readValue(raw,type){
 if(type==='BOOLEAN')return raw==='true';
 if(!numeric(type))return raw;
 const value=Number(raw.trim().replace(',','.'));
 if(!raw.trim()||!Number.isFinite(value))throw new Error('Введите число, например 1,25.');
 if(type==='INTEGER'&&!Number.isInteger(value))throw new Error('Введите целое число.');
 return value;
}

export function guidedFunctionEditor(draft,functions,onProgress,templateParameters){
 const root=document.createElement('section');
 root.className='order-function-body';
 let path=missingPath(draft.expression);
 let category=null;
 const locate=indices=>indices.reduce((node,index)=>node.args[index],draft.expression);
 function setAnswer(answer){
  const previousType=resultOf(draft.expression,draft.parameters,functions,templateParameters);
  if(path.length)locate(path.slice(0,-1)).args[path.at(-1)]=answer;
  else draft.expression=answer;
  const currentType=resultOf(draft.expression,draft.parameters,functions,templateParameters);
  if(currentType&&currentType!==previousType)draft.resultType=currentType;
  path=missingPath(draft.expression);
  category=null;
  render(true);
 }
 function addEditSteps(panel,entries){
  const details=document.createElement('details');
  details.className='order-function-code';
  details.innerHTML=`<summary>Изменить отдельный шаг</summary><div class="order-guided-actions">${entries.map(({node,key},i)=>choose(`edit-${i}`,key.length?guidedFunctionText(node,draft.parameters,functions,templateParameters):templateParameters?'Условие правила':'Задача функции')).join('')}</div>`;
  details.addEventListener('click',event=>{
   const choice=event.target.closest('[data-choose]');
   if(!choice)return;
   path=entries[Number(choice.dataset.choose.slice(5))].key;
   render(true);
  });
  panel.append(details);
 }
 function render(focus=false){
  const entries=[];
  const summary=guidedFunctionText(draft.expression,draft.parameters,functions,templateParameters);
  root.innerHTML=draft.expression?`<div class="order-function-preset"><p class="order-function-preview">${esc(summary)}</p></div>`:'';
  const panel=document.createElement('div');
  panel.className='order-function-body';
  root.append(panel);
  const visit=(node,key)=>{
   if(!node)return;
   entries.push({node,key});
   node.args.forEach((child,index)=>visit(child,[...key,index]));
  };
  visit(draft.expression,[]);
  onProgress(path===null);
  if(path===null){
   panel.innerHTML='<p class="orders-empty" tabindex="-1">Условие готово. Нажмите «Далее», чтобы попробовать его на примере.</p>';
   addEditSteps(panel,entries);
   if(focus)panel.querySelector('p').focus();
   return;
  }
  const parent=path.length?locate(path.slice(0,-1)):null;
  const index=path.at(-1);
  const node=locate(path);
  const expected=dataTypeFor(parent,index,draft.parameters,functions,templateParameters);
  const group=groups.find(item=>item.id===category);
  const listType=resultOf(parent?.args[0],draft.parameters,functions,templateParameters);
  const matchingParameter=templateParameter(parent?.args[index===0?1:0],templateParameters);
  const enumOptions=matchingParameter?.data_type==='ENUM'?matchingParameter.options:[];
  const availableFields=templateParameters?.filter(p=>compatible(p.data_type,expected))||[];
  const available=draft.parameters.filter(p=>compatible(p.dataType,expected));
  const callable=functions.filter(f=>compatible(f.result_type,expected));
  const supportedTypes=inputTypes.filter(([type])=>compatible(type,expected));
  panel.innerHTML=`<h4 tabindex="-1">${esc(question(parent,index,functions,Boolean(templateParameters)))}</h4>
   <div class="orders-error" role="alert" data-answer-error hidden></div>`;
  const choices=document.createElement('div');
  choices.className='order-guided-actions';
  panel.append(choices);
  if(!category&&expected!=='LIST'){
   const opChoices=path.length<35?groups.filter(g=>compatible(g.result,expected)).map(g=>choose(g.id,g.label)):[];
   const valueChoices=[
    ...(availableFields.length?[choose('field','Выбрать параметр изделия')]:[]),
    ...(!templateParameters&&supportedTypes.length?[choose('input','Попросить ввести значение')]:[]),
    ...(supportedTypes.length?[choose('constant','Подставить постоянное значение')]:[]),
   ];
   choices.innerHTML=[
    ...available.map(p=>choose(`input:${p.code}`,parameterCaption(p))),
    ...(parent?[...valueChoices,...opChoices]:[...opChoices,...valueChoices]),
    ...(callable.length&&path.length<35?[choose('call','Вызвать функцию из библиотеки')]:[]),
   ].join('');
   choices.addEventListener('click',event=>{
    const button=event.target.closest('[data-choose]');
    if(!button)return;
    const value=button.dataset.choose;
    if(value.startsWith('input:'))setAnswer({kind:'FORMAL',formalCode:value.slice(6),args:[]});
    else if(value==='list')setAnswer({kind:'OPERATOR',operator:'IN',args:node?.operator==='IN'?node.args:[null,null]});
    else{
     category=value;
     render(true);
    }
   });
  }else{
   const initialType=resultOf(node,draft.parameters,functions,templateParameters);
   const initial=supportedTypes.find(([type])=>type===initialType)?.[0]??supportedTypes[0]?.[0];
   if(group){
    const label=group.id==='compare'?'Как сравнить?':'Что сделать?';
    choices.innerHTML=group.options.length===1?'':`<label class="order-control">${label}
     <select class="select" data-option aria-label="${label}">${options(group.options,node?.operator)}</select></label>`;
   }else if(category==='call'){
    choices.innerHTML=`<label class="order-control">Какую функцию использовать?
     <select class="select" data-option aria-label="Какую функцию использовать?">${options(callable.map(f=>[f.id,f.name]),node?.functionId)}</select></label>`;
   }else if(category==='field'){
    choices.innerHTML=`<label class="order-control">Какой параметр использовать?
     <select class="select" data-option aria-label="Какой параметр использовать?">${options(availableFields.map(p=>[p.code,templateCaption(p)]),node?.parameterCode)}</select></label>`;
   }else if(expected==='LIST'&&enumOptions?.length){
    choices.innerHTML=`<fieldset class="order-control"><legend>Выберите допустимые варианты</legend>
     ${enumOptions.map(option=>`<label><input type="checkbox" data-list-value value="${esc(option.code)}" ${node?.value?.includes?.(option.code)?'checked':''}> ${esc(option.name)}</label>`).join('')}
     </fieldset>`;
   }else if(expected==='LIST'){
    choices.innerHTML=`<label class="order-control">По одному значению в строке
     <textarea class="textarea" rows="4" data-value>${esc(Array.isArray(node?.value)?node.value.join('\n'):'')}</textarea></label>`;
   }else if(category==='constant'&&enumOptions?.length){
    choices.innerHTML=`<label class="order-control">С каким вариантом сравнить?
     <select class="select" data-value aria-label="С каким вариантом сравнить?">${options(enumOptions.map(option=>[option.code,option.name]),node?.value)}</select></label>`;
   }else{
    const typeChoice=supportedTypes.length>1?`<label class="order-control">Что это за значение?
      <select class="select" data-type>${options(supportedTypes,initial)}</select></label>`:'';
    choices.innerHTML=typeChoice+(category==='input'
     ?'<label class="order-control">Как назвать это значение?<input class="input" data-value maxlength="255" placeholder="Например, доступная мощность"></label>'
     :`<label class="order-control" data-literal>Какое значение подставить?${valueControl(initial,node?.value)}</label>`);
    choices.querySelector('[data-type]')?.addEventListener('change',event=>{
     const literal=choices.querySelector('[data-literal]');
     if(literal)literal.innerHTML=`Какое значение подставить?${valueControl(event.target.value,'')}`;
    });
   }
   const accept=document.createElement('button');
   accept.type='button';accept.className='btn btn--primary';accept.textContent='Готово';
   panel.append(accept);
   accept.addEventListener('click',()=>{
    try{
     const selected=choices.querySelector('[data-option]')?.value;
     const type=choices.querySelector('[data-type]')?.value??initial;
     const value=choices.querySelector('[data-value]')?.value;
     if(group){
      const operator=selected??group.options[0][0];
      const arity=unary.has(operator)?1:2;
      const keepValues=node?.operator===operator||
       (groupOf(node?.operator)===group&&node.args.length===arity);
      const args=keepValues?node.args:Array.from({length:arity},()=>null);
      setAnswer({kind:'OPERATOR',operator,args});
     }else if(category==='call'){
      const functionId=Number(selected);
      const args=node?.functionId===functionId?node.args:callable.find(f=>f.id===functionId).parameters.map(()=>null);
      setAnswer({kind:'CALL',functionId,args});
     }else if(category==='field'){
      setAnswer({kind:'PARAMETER',parameterCode:selected,args:[]});
     }else if(expected==='LIST'&&enumOptions?.length){
      const values=[...choices.querySelectorAll('[data-list-value]:checked')].map(input=>input.value);
      if(!values.length)throw new Error('Выберите хотя бы один вариант.');
      setAnswer({kind:'CONST',value:values,args:[]});
     }else if(expected==='LIST'){
      const values=value.split(/\r?\n/).map(item=>item.trim()).filter(Boolean);
      if(!values.length)throw new Error('Введите хотя бы одно значение.');
      const items=values.map(item=>listType==='BOOLEAN'
       ?readBoolean(item)
       :readValue(item,listType));
      setAnswer({kind:'CONST',value:items,args:[]});
     }else if(category==='input'){
      const displayName=value.trim();
      if(!displayName)throw new Error('Напишите, что человек должен ввести.');
      if(draft.parameters.some(p=>parameterCaption(p).toLowerCase()===displayName.toLowerCase())){
       throw new Error('Такое значение уже есть. Выберите его в предыдущем списке.');
      }
      let i=draft.parameters.length+1;
      while(draft.parameters.some(p=>p.code===`value_${i}`))i++;
      const code=`value_${i}`;
      draft.parameters.push({code,dataType:type,displayName});
      setAnswer({kind:'FORMAL',formalCode:code,args:[]});
     }else setAnswer({kind:'CONST',value:readValue(value,type),args:[]});
    }catch(e){
     const error=panel.querySelector('[data-answer-error]');
     error.textContent=e.message;
     error.hidden=false;
    }
   });
   panel.addEventListener('keydown',event=>{
    if(event.key==='Enter'&&event.target.matches('input,select')){
     event.preventDefault();
     accept.click();
    }
   });
  }
  if(category||node){
   const change=document.createElement('button');
   change.type='button';change.className='btn btn--secondary';
   change.textContent=category?'Назад к вариантам':'Отменить изменение';
   change.addEventListener('click',()=>{
    if(category)category=null;
    else path=missingPath(draft.expression);
    render(true);
   });
   panel.append(change);
  }
  if(!category&&!node&&entries.length)addEditSteps(panel,entries);
  if(focus)panel.querySelector('h4')?.focus();
 }
 render();
 return root;
}

function readBoolean(raw){
 const value=raw.toLowerCase();
 if(value==='да'||value==='нет')return value==='да';
 throw new Error('Для списка ответов вводите «Да» или «Нет» в каждой строке.');
}
