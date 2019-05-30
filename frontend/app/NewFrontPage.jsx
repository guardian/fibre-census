import React from 'react';
import axios from 'axios';
import DisplaySimpleText from './displayboxes/DisplaySimpleText.jsx';
import DisplayRecentUsers from "./displayboxes/DisplayRecentUsers.jsx";
import DisplayTextList from "./displayboxes/DisplayTextList.jsx";
import DisplayTimestamp from "./displayboxes/DisplayTimestamp.jsx";
import DisplayMdcPing from "./displayboxes/DisplayMdcPing.jsx";
import DisplayFibreDrivers from "./displayboxes/DisplayFibreDrivers.jsx";

class NewFrontPage extends React.Component {
    constructor(props){
        super(props);
        this.state = {
            searchTerm: "",
            results: [],
            totalHitCount: null,
            loading: false,
            lastError: null,
            showRelativeTime: true,
            showDriverDetails: true
        };

    }

    componentWillMount(){
        this.refresh();
    }

    mapOutData(rawData){
        const fcData = rawData.fibreChannel!==null ? {
            "fcWWN": rawData.fibreChannel.domains.map(dom=>dom.portWWN),
            "fcLunCount": rawData.fibreChannel.domains.map(dom=>dom.lunCount),
            "fcSpeed": rawData.fibreChannel.domains.map(dom=>dom.speed ? dom.speed : <span className="small-info">not connected</span>),
            "fcStatus": rawData.fibreChannel.domains.map(dom=>dom.status ? dom.status : <span className="small-info">not connected</span>),
            "fcAdaptor": rawData.fibreChannel.productName,
            "driverInfo": rawData.driverInfo ? rawData.driverInfo : null
        } : {
            "fcWWN": ["Not present"],
            "fcLunCount": ["Not present"],
            "fcSpeed": ["Not present"],
            "fcStatus": ["Not present"],
            "fcAdaptor": "Not present",
            "driverInfo": rawData.driverInfo ? rawData.driverInfo : null
        };


        return Object.assign({
            "hostName": rawData.hostName,
            "computerName": rawData.computerName,
            "model": rawData.model,
            "hwUUID": rawData.hwUUID,
            "ipAddresses": rawData.ipAddresses,
            "lastUpdate": rawData.lastUpdate,
            "denyDlcVolumes": rawData.denyDlcVolumes,
            "mdcPing": rawData.mdcPing,
            "sanMounts": rawData.sanMounts
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

    render(){
        return <div>
            <h1>Fibre Census</h1>

            <div className="centered">
                <label htmlFor="search-box">Search:</label>
                <input type="text" id="search-box" style={{width: "50%"}} onChange={this.updateSearchTerms}/>
                <img style={{marginLeft:"auto",marginRight:"auto",width:"44px", display: this.state.loading ? "inline" : "none" }} src="/assets/images/Spinner-1s-44px.svg"/>
                <input type="checkbox" checked={this.state.showDriverDetails} id="driver-details-check" onChange={event=>this.setState({showDriverDetails: !this.state.showDriverDetails})}/>
                <label htmlFor="driver-details-check">Show driver details</label>
            </div>

            <ul className="boxlist">
                {
                    this.state.data.map(entry=><li key={entry.hostName} className="entry-container">
                        <span className="entry-header">{entry.hostName}</span>
                        <DisplayFibreDrivers title="Fibre drivers present"
                                             listData={entry.driverInfo}
                                             showDetails={this.state.showDriverDetails}
                                             extraClasses="oversized float-right"/>
                        <DisplaySimpleText title="Model" entry={entry.model}/>
                        <DisplaySimpleText title="Computer Name" entry={entry.computerName} extraClasses="wider"/>
                        <DisplayTextList title="IP Addresses" listData={entry.ipAddresses}/>
                        <DisplayRecentUsers title="Recent Users" entry={entry} extraClasses="doublewidth"/>
                        <DisplayTextList title="Fibre WWNs" listData={entry.fcWWN} extraClasses="wider"/>
                        <DisplayTextList title="Fibre status" listData={entry.fcStatus}/>
                        <DisplayTextList title="DenyDLC" listData={entry.denyDlcVolumes}/>
                        <DisplayTimestamp title="Snapshot taken" entry={entry.lastUpdate}/>
                        <DisplayTextList title="Fibre speed" listData={entry.fcSpeed}/>
                        <DisplayTextList title="Fibre LUN count" listData={entry.fcLunCount}/>
                        <DisplaySimpleText title="Fibre adaptor model" entry={entry.fcAdaptor} extraClasses="wider"/>
                        <DisplayMdcPing title="MDC Controller Connectivity" listData={entry.mdcPing} extraClasses="doublewidth"/>
                        <DisplayTextList title="SAN mounts" listData={entry.sanMounts ? entry.sanMounts.map(m=>m.name) : null}/>
                    </li> )
                }

            </ul>
        </div>
    }
}

export default NewFrontPage;
