import React from 'react';
import ReactTable from 'react-table';
import axios from 'axios';
import { ReactTableDefaults } from 'react-table';
import 'react-table/react-table.css'

import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import TimestampFormatter from "./common/TimestampFormatter.jsx";
import ErrorViewComponent from "./common/ErrorViewComponent.jsx";
import UserHistoryComponent from './UserHistoryComponent.jsx';
import ReactTooltip from 'react-tooltip';
import {validateRecord} from './validation.jsx';

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
                Header:"Host Name",
                accessor: "hostName",
                defaultSorting: "desc",
            },
            {
                Header:"Model",
                accessor: "model",
            },
            {
                Header:"Computer Name",
                accessor: "computerName",
            },
            {
                Header:"Last User",
                accessor: "hostName",
                Cell: (props)=><UserHistoryComponent hostname={props.value} limit={1}/>
            },
            {
                Header:"IP Addressess",
                accessor: "ipAddresses",
                Cell: (props)=><ul className="addressList">{props.value.map(entry=><li key={entry}>{entry}</li>)}</ul>,
            },
            {
                Header:"Fibre WWNs",
                accessor: "fcWWN",
                Cell: (props)=><ul className="addressList">{props.value.map(entry=><li key={entry}>{entry}</li>)}</ul>,
            },
            {
                Header:"Fibre Status",
                accessor: "fcStatus",
                Cell: (props)=><ul className="addressList">{props.value.map(entry=><li key={entry}>{entry}</li>)}</ul>,
            },
            {
                Header:"Fibre Configured Speed",
                accessor: "fcSpeed",
                Cell: (props)=><ul className="addressList">{props.value.map(entry=><li key={entry}>{entry}</li>)}</ul>,
            },
            {
                Header:"Fibre LUN count",
                accessor: "fcLunCount",
                Cell: (props)=><ul className="addressList">{props.value.map(entry=><li key={entry}>{entry}</li>)}</ul>,
            },
            {
                Header:"Fibre adaptor model",
                accessor: "fcAdaptor",
            },
            {
                Header: "DenyDLC active on",
                accessor: "denyDlcVolumes",
                Cell: (props)=>props.value ?
                    <ul className="addressList">{props.value.map(entry=><li key={entry}><FontAwesomeIcon icon="hdd" style={{marginRight: "0.5em"}}/>{entry}</li>)}</ul> :
                    <span className="small-info">none</span>
                ,
            },
            {
                Header:"Snapshot time",
                accessor: "lastUpdate",
                Cell: (props)=><TimestampFormatter relative={this.state.showRelativeTime} value={props.value}/>,
            }
        ];

        this.state = {
            searchTerm: "",
            results: [],
            totalHitCount: null,
            loading: false,
            lastError: null,
            showRelativeTime: true
        };

        this.updateSearchTerms = this.updateSearchTerms.bind(this);
    }

    componentWillMount(){
        this.refresh();
    }

    rowValidationProps(state,rowInfo,column) {
        if(rowInfo) {
            return {
                className: validateRecord(rowInfo.row)
            }
        } else {
            return {};
        }

    }

    mapOutData(rawData){
        const fcData = rawData.fibreChannel!==null ? {
            "fcWWN": rawData.fibreChannel.domains.map(dom=>dom.portWWN),
            "fcLunCount": rawData.fibreChannel.domains.map(dom=>dom.lunCount),
            "fcSpeed": rawData.fibreChannel.domains.map(dom=>dom.speed ? dom.speed : <span className="small-info">not connected</span>),
            "fcStatus": rawData.fibreChannel.domains.map(dom=>dom.status ? dom.status : <span className="small-info">not connected</span>),
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
            "lastUpdate": rawData.lastUpdate,
            "denyDlcVolumes": rawData.denyDlcVolumes,
        }, fcData)
    }

    refresh(){
        const searchTerm = encodeURIComponent(this.state.searchTerm ? this.state.searchTerm : "*");

        this.setState({loading: true, lastError:null, data:[]}, ()=>axios.get("/api/search/basic?q=" + searchTerm + "&length=100").then(result=>{
            this.setState({data: result.data.entries.map(entry=>this.mapOutData(entry)), totalHitCount: result.data.entryCount, loading: false, lastError: null});
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
                    this.state.totalHitCount ? <span className="small-info">Showing {this.state.data.length} results from a total of {this.state.totalHitCount}</span> : <span/>
                }
            </div>
            <div style={{marginTop: "0.5em"}}>
            {
                this.state.lastError ? <ErrorViewComponent error={this.state.lastError}/> : <ReactTable
                    data={this.state.data}
                    columns={this.columns}
                    column={Object.assign({}, ReactTableDefaults.column, {headerClassName: 'dashboardheader'})}
                    getTrProps={this.rowValidationProps}
                />
            }
            </div>
            <ReactTooltip className="generic-tooltip"/>
        </div>

    }
}

export default FrontPage;