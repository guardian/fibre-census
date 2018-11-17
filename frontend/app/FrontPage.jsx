import React from 'react';
import SortableTable from 'react-sortable-table';
import TimestampFormattr from './common/TimestampFormatter.jsx';
import axios from 'axios';

import { FontAwesomeIcon } from '@fortawesome/react-fontawesome'
import TimestampFormatter from "./common/TimestampFormatter.jsx";
import ErrorViewComponent from "./common/ErrorViewComponent.jsx";
import UserHistoryComponent from './UserHistoryComponent.jsx';
import ReactTooltip from 'react-tooltip';

class FrontPage extends React.Component {
    constructor(props){
        super(props);
        this.style = {
            backgroundColor: '#eee',
            border: '1px solid black',
            borderCollapse: 'collapse'
        };

        this.iconStyle = {
            color: '#aaa',
            paddingLeft: '5px',
            paddingRight: '5px'
        };

        this.columns = [
            {
                header: "Host Name",
                key: "hostName",
                defaultSorting: "desc",
                headerProps: { className: 'dashboardheader'}
            },
            {
                header: "Model",
                key: "model",
                headerProps: { className: 'dashboardheader'}
            },
            {
                header: "Computer Name",
                key: "computerName",
                headerProps: { className: 'dashboardheader'}
            },
            {
                header: "Last User",
                key: "hostName",
                headerProps: { className: 'dashboardheader'},
                render: (hostname)=><UserHistoryComponent hostname={hostname} limit={1}/>
            },
            {
                header: "IP Addressess",
                key: "ipAddresses",
                render: (value)=><ul className="addressList">{value.map(entry=><li key={entry}>{entry}</li>)}</ul>,
                headerProps: { className: 'dashboardheader'}
            },
            {
                header: "Fibre WWNs",
                key: "fcWWN",
                render: (value)=><ul className="addressList">{value.map(entry=><li key={entry}>{entry}</li>)}</ul>,
                headerProps: { className: 'dashboardheader'}
            },
            {
                header: "Fibre Status",
                key: "fcStatus",
                render: (value)=><ul className="addressList">{value.map(entry=><li key={entry}>{entry}</li>)}</ul>,
                headerProps: { className: 'dashboardheader'}
            },
            {
                header: "Fibre Configured Speed",
                key: "fcSpeed",
                render: (value)=><ul className="addressList">{value.map(entry=><li key={entry}>{entry}</li>)}</ul>,
                headerProps: { className: 'dashboardheader'}
            },
            {
                header: "Fibre LUN count",
                key: "fcLunCount",
                render: (value)=><ul className="addressList">{value.map(entry=><li key={entry}>{entry}</li>)}</ul>,
                headerProps: { className: 'dashboardheader'}
            },
            {
                header: "Fibre adaptor model",
                key: "fcAdaptor",
                headerProps: { className: 'dashboardheader'}
            },
            {
                header: "Snapshot time",
                key: "lastUpdate",
                render: (value)=><TimestampFormatter relative={this.state.showRelativeTime} value={value}/>,
                headerProps: { className: 'dashboardheader'}
            }
        ];

        this.state = {
            searchTerm: "",
            results: [],
            loading: false,
            lastError: null,
            showRelativeTime: true
        };

        this.updateSearchTerms = this.updateSearchTerms.bind(this);
    }

    componentWillMount(){
        this.refresh();
    }

    mapOutData(rawData){
        const fcData = rawData.fibreChannel!==null ? {
            "fcWWN": rawData.fibreChannel.domains.map(dom=>dom.portWWN),
            "fcLunCount": rawData.fibreChannel.domains.map(dom=>dom.lunCount),
            "fcSpeed": rawData.fibreChannel.domains.map(dom=>dom.speed ? dom.speed : <span className="small-info">not connected</span>),
            "fcStatus": rawData.fibreChannel.domains.map(dom=>dom.fcStatus ? dom.fcStatus : <span className="small-info">not connected</span>),
            "fcAdaptor": rawData.fibreChannel.productName,
        } : {
            "fcWWN": ["Not present"],
            "fcLunCount": ["Not present"],
            "fcSpeed": ["Not present"],
            "fcStatus": ["Not present"],
            "fcAdaptor": "Not present"
        };


        return Object.assign({
            "hostName": rawData.hostName,
            "computerName": rawData.computerName,
            "model": rawData.model,
            "hwUUID": rawData.hwUUID,
            "ipAddresses": rawData.ipAddresses,
            "lastUpdate": rawData.lastUpdate
        }, fcData)
    }

    refresh(){
        const searchTerm = encodeURIComponent(this.state.searchTerm ? this.state.searchTerm : "*");

        this.setState({loading: true, lastError:null, data:[]}, ()=>axios.get("/api/search/basic?q=" + searchTerm).then(result=>{
            this.setState({data: result.data.entries.map(entry=>this.mapOutData(entry)), loading: false, lastError: null});
        }).catch(err=>{
            console.error(err);
            this.setState({loading: false, lastError: err});
        }));
    }

    updateSearchTerms(evt){
        this.setState({searchTerm: evt.target.value}, ()=>this.refresh());
    }

    render(){
        return <div>
            <h1>Fibre Census</h1>
            <div className="centered">
                <label htmlFor="search-box">Search:</label>
                <input type="text" id="search-box" style={{width: "50%"}} onChange={this.updateSearchTerms}/>
                <img style={{marginLeft:"auto",marginRight:"auto",width:"44px", display: this.state.loading ? "inline" : "none" }} src="/assets/images/Spinner-1s-44px.svg"/>
            </div>
            <div style={{marginTop: "1em"}}>
            {
                this.state.lastError ? <ErrorViewComponent error={this.state.lastError}/> : <SortableTable
                    data={this.state.data}
                    columns={this.columns}
                    style={this.style}
                    iconStyle={this.iconStyle}
                    tableProps={{className: "dashboardpanel"}}
                />
            }
            </div>
            <ReactTooltip className="generic-tooltip"/>
        </div>

    }
}

export default FrontPage;