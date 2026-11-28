import { Component, ChangeDetectionStrategy } from '@angular/core';
import { MatDialog, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { Router } from '@angular/router';
import { Inject, ViewEncapsulation } from '@angular/core';
import { DatasourceService } from '../../core/services/datasource.service';
import { MatButton } from '@angular/material/button';


@Component({
    selector: 'app-config-datasource-modal',
    templateUrl: './config-datasource-modal.component.html',
    styleUrls: ['./config-datasource-modal.component.scss'],
    encapsulation: ViewEncapsulation.None,
    changeDetection: ChangeDetectionStrategy.Eager,
    imports: [MatButton]
})
export class ConfigDatasourceModalComponent {

  associatedRoutes!: string[];

  constructor(public dialog: MatDialog, @Inject(MAT_DIALOG_DATA) public data: any, public router: Router, private datasourceService: DatasourceService) { }



  ngOnInit(): void {
  }

  closeDialog(): void {
    this.dialog.closeAll();
  }


  configDatasource(){
    this.datasourceService.modifyDatasource(this.data.datasource).subscribe(()=>{
      this.closeDialog();
      this.router.navigate(['../datasources']);
    });
}

}
