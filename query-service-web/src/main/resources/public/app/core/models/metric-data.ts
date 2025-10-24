export interface RouteMetric {
    route: string;
    maxProcessingTime: number;
    exchangesFailed: number;
    exchangesInflight: number;
    exchangesTotal: number;
    meanProcessingTime: number;
    minProcessingTime: number;
    state: string;
    totalProcessingTime: number;
    exchangesCompleted: number;
    uptime: string;
    timeStamp: string;
  }
  
  export interface RouteMetricsResponse {
    metrics: RouteMetric[];
  }
  