import { Inject, ViewEncapsulation } from '@angular/core';
import { Component, OnInit } from '@angular/core';
import { MatDialog, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { Router } from '@angular/router';
import { DatasourceService } from '../../core/services/datasource.service';


@Component({
  selector: 'app-confirm-dialog',
  templateUrl: './delete-datasource-modal.component.html',
  styleUrls: ['./delete-datasource-modal.component.scss'],
  encapsulation: ViewEncapsulation.None
})
export class DeleteDatasourceModalComponent implements OnInit {
 
  associatedRoutes!: string[];

  constructor(public dialog: MatDialog, @Inject(MAT_DIALOG_DATA) public data: any, public router: Router, private datasourceService: DatasourceService) { }



  ngOnInit(): void {
  }

  closeDialog(): void {
    this.dialog.closeAll();
  }


  deleteDatasource(datasourceId: string){
    this.datasourceService.deleteDatasource(datasourceId).subscribe(()=>{
      this.closeDialog();
      this.router.navigate(['../datasources']);
    });
}
}
