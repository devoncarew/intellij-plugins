import {Component} from '@angular/core';

@Component({   
  selector: 'my-appww',  
  template: `<div [style.font-size]="title ? 'medium' : 'small'"></div>`
})      
export class AppComponent {    
  title = 'Tour of Heroes'; 
  heroes = HEROES;
  selectedHero = {firstName: "eee"}
}
