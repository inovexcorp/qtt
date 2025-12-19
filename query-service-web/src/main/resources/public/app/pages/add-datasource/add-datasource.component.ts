import {Component, OnInit, ViewEncapsulation} from '@angular/core';
import {FormControl, FormGroup, Validators} from '@angular/forms';
import {AddDatasourceService} from './add-datasource.service';
import {Router} from '@angular/router';
import {Datasources} from '../../core/models/datasources';
import {DatasourceService} from "../../core/services/datasource.service";

@Component({
  selector: 'app-add-datasource',
  templateUrl: './add-datasource.component.html',
  styleUrls: ['./add-datasource.component.scss'],
  encapsulation: ViewEncapsulation.None
})
export class AddDatasourceComponent implements OnInit {

  createDatasource = new FormGroup({
    dataSourceId: new FormControl('', Validators.required),
    url: new FormControl('', Validators.required),
    timeOutSeconds: new FormControl('', Validators.required),
    maxQueryHeaderLength: new FormControl('', Validators.required),
    username: new FormControl('', Validators.required),
    password: new FormControl('', Validators.required),
    validateCertificate: new FormControl('true', Validators.required)
  })
  hide = true;
  testResponse: any;
  testResponseStatus: 'success' | 'error' | null = null;
  healthCheckEnabled: boolean = true;

  get dataSourceId() {
    return this.createDatasource.get('dataSourceId')
  }

  get url() {
    return this.createDatasource.get('url');
  }

  get timeOutSeconds() {
    return this.createDatasource.get('timeOutSeconds');
  }

  get maxQueryHeaderLength() {
    return this.createDatasource.get('maxQueryHeaderLength');
  }

  get username() {
    return this.createDatasource.get('username');
  }

  get password() {
    return this.createDatasource.get('password');
  }

  get validateCertificate() {
    return this.createDatasource.get('validateCertificate');
  }

  constructor(
    public router: Router,
    private addDatasourceService: AddDatasourceService,
    private datasourceService: DatasourceService,
  ) {
  }

  ngOnInit(): void {
    this.checkHealthCheckStatus();
  }

  private checkHealthCheckStatus(): void {
    this.datasourceService.getHealthCheckConfig().subscribe({
      next: (config) => {
        this.healthCheckEnabled = config.enabled && config.available;
      },
      error: (err) => {
        console.error('Failed to fetch health check configuration:', err);
        this.healthCheckEnabled = true;
      }
    });
  }

  add(): void {
    let dataSourceId: string = this.createDatasource.value['dataSourceId'] as string;
    let url: string = this.createDatasource.value['url'] as string;
    let timeOutSeconds: string = this.createDatasource.value['timeOutSeconds'] as string;
    let maxQueryHeaderLength: string = this.createDatasource.value['maxQueryHeaderLength'] as string;
    let username: string = this.createDatasource.value['username'] as string;
    let password: string = this.createDatasource.value['password'] as string;
    let validateCertificate: boolean = this.createDatasource.value['validateCertificate'] === 'true';
    if (!dataSourceId || !url || !timeOutSeconds || !maxQueryHeaderLength || !username || !password) {
      return;
    }

    this.addDatasourceService.postDatasource({
      dataSourceId,
      url,
      timeOutSeconds,
      maxQueryHeaderLength,
      username,
      password,
      validateCertificate
    } as Datasources)
      .subscribe(response => {
        this.router.navigate(['../../datasources']);
      });
  }

  test(): void {
    this.datasourceService.testDatasource(this.asDataSource()).subscribe({
      next: response => {
        this.testResponseStatus = response.status;
        this.testResponse = this.testResponseStatus === 'success' ? null : response.message;
      },
      error: err => {
        this.testResponseStatus = 'error';
        this.testResponse = err.error ?? err;
        console.error(err);
      }
    });
  }

  private asDataSource(): Datasources {
    return {
      "dataSourceId": this.createDatasource.value['dataSourceId'] as string,
      "url": this.createDatasource.value['url'] as string,
      "timeOutSeconds": this.createDatasource.value['timeOutSeconds'] as string,
      "maxQueryHeaderLength": this.createDatasource.value['maxQueryHeaderLength'] as string,
      "username": this.createDatasource.value['username'] as string,
      "password": this.createDatasource.value['password'] as string,
      "validateCertificate": this.createDatasource.value['validateCertificate'] === 'true'
    } as Datasources;
  }
}
