import { Datasources } from "../datasources";

export interface TableData {
    routeId: string;
    description: string;
    datasource: string;
    datasources: Datasources;
    templateName: string;
    routeParams: string;
    status: string;
    graphMartUri: string;
  }