import { Component, Inject, OnInit, ViewEncapsulation } from '@angular/core';
import { MatDialog, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { DeleteRouteModalService } from './delete-route-modal.service';
import { Router } from '@angular/router';


@Component({
  selector: 'app-delete-route-modal',
  templateUrl: './delete-route-modal.component.html',
  styleUrls: ['./delete-route-modal.component.scss'],
  encapsulation: ViewEncapsulation.None
})
export class DeleteRouteModalComponent implements OnInit {

  routeId: string = this.data.routeId;

  constructor(public dialog: MatDialog, @Inject(MAT_DIALOG_DATA) public data: any, private deleteRouteModalService: DeleteRouteModalService, public router: Router ) { }

  closeDialog(): void {
    this.dialog.closeAll();
  }

  ngOnInit(): void {
  }

  deleteRoute(routeId: string){
    this.deleteRouteModalService.deleteRoute(routeId).subscribe(response=>{
      this.router.navigate(['../../routes']);
      location.reload();
    });
}

}
