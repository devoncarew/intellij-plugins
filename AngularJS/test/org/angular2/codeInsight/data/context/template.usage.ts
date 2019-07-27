// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import {Component} from '@angular/core';

@Component({
    templateUrl: "./template.usage.html",
    selector: 'todo-cmp',
})
export class TodoCmp {
    onCompletedButton() {
        this.other = [1, 2, 3];
    }
}

new TodoCmp
