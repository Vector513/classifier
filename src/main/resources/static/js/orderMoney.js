export function money(value){
 if(value==null)return 'Не рассчитана';
 const [whole,fraction='']=String(value).split('.');
 return `${whole.replace(/\B(?=(\d{3})+(?!\d))/g,'\u00a0')},${fraction.padEnd(2,'0')}\u00a0₽`;
}
