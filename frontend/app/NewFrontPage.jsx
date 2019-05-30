import React from 'react';
import axios from 'axios';
import DisplaySimpleText from './displayboxes/DisplaySimpleText.jsx';
import DisplayRecentUsers from "./displayboxes/DisplayRecentUsers.jsx";
import DisplayTextList from "./displayboxes/DisplayTextList.jsx";
import DisplayMdcPing from "./displayboxes/DisplayMdcPing.jsx";
import DisplayFibreDrivers from "./displayboxes/DisplayFibreDrivers.jsx";
import TimestampFormatter from "./common/TimestampFormatter.jsx";
import {validateRecord} from './validation.jsx';
import ValidateModel from "./validation/ValidateModel.jsx";
import ReactTooltip from "react-tooltip";
import ValidateIpAddresses from "./validation/ValidateIpAddresses.jsx";
import ValidateFCWWN from "./validation/ValidateFCWWN.jsx";
import ValidateLunCount from "./validation/ValidateLunCount.jsx";
import ValidateSanVolumes from "./validation/ValidateSanVolumes.jsx";
import ValidateFibreDrivers from "./validation/ValidateFibreDrivers.jsx";
import ValidateMdcPing from "./validation/ValidateMdcPing.jsx";
import ProblemsFilter from "./ProblemsFilter.jsx";
import SortSelector from "./SortSelector.jsx";
import moment from 'moment';

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
            showDriverDetails: true,
            currentFilter: "all",
            filteredHitsCount: 0,
            sortField: "time",
            sortOrder: "descending",
            sortDone: false
        };
        this.updateSearchTerms = this.updateSearchTerms.bind(this);
        this.sortUpdated = this.sortUpdated.bind(this);
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


        const initialMappedData = Object.assign({
            "hostName": rawData.hostName,
            "computerName": rawData.computerName,
            "model": rawData.model,
            "hwUUID": rawData.hwUUID,
            "ipAddresses": rawData.ipAddresses,
            "lastUpdate": rawData.lastUpdate,
            "denyDlcVolumes": rawData.denyDlcVolumes,
            "mdcPing": rawData.mdcPing,
            "sanMounts": rawData.sanMounts
        }, fcData);

        return Object.assign({
            validation:  validateRecord(initialMappedData)
        }, initialMappedData);
    }

    componentDidUpdate(prevProps, prevState, snapshot) {
        console.log("componentDidUpdate from: ", prevState);
        console.log("componentDidUpdate to: ", this.state);
        if(prevState.currentFilter!==this.state.currentFilter || prevState.data!==this.state.data){
            console.log("refiltering: ", this.state.currentFilter);
            const filteredHits = this.state.data.filter(entry=>this.state.currentFilter==="all" || this.state.currentFilter===entry.validation);
            console.log("filteredHits: ", filteredHits);
            this.setState({filteredHitsCount: filteredHits.length});
        }
    }

    refresh(){
        const searchTerm = encodeURIComponent(this.state.searchTerm ? this.state.searchTerm : "*");

        this.setState({loading: true, lastError:null, data:[]}, ()=>axios.get("/api/search/basic?q=" + searchTerm + "&length=100").then(result=>{
            this.setState({data: result.data.entries.map(entry=>this.mapOutData(entry)), totalHitCount: result.data.entryCount, loading: false, lastError: null, sortDone: false}, ()=>this.doSort());
        }).catch(err=>{
            console.error(err);
            this.setState({loading: false, lastError: err, sortDone: true});
        }));
    }

    shouldComponentUpdate(nextProps, nextState, nextContext) {
        if(!nextState.sortDone) return false;
        return true
    }

    doSort(){
        const hostnameSortFunc = (a,b)=>{
            const hostnameA = a.hostName;
            const hostnameB = b.hostName;
            return this.state.sortOrder==="ascending" ? hostnameA.localeCompare(hostnameB) : hostnameB.localeCompare(hostnameA);
        };

        const updateTimeSortFunc = (a,b)=>{
            const updatedA = moment(a.lastUpdate);
            const updatedB = moment(b.lastUpdate);

            return this.state.sortOrder==="ascending" ? updatedB.isBefore(updatedA) : updatedA.isBefore(updatedB);
        };

        let currentSortFunc;
        if(this.state.sortField==="hostname") currentSortFunc = hostnameSortFunc;
        if(this.state.sortField==="time") currentSortFunc = updateTimeSortFunc;

        const sorted = this.state.data.sort(currentSortFunc);
        this.setState({data: sorted, sortDone: true});

    }

    updateSearchTerms(evt){
        this.setState({searchTerm: evt.target.value}, ()=>this.refresh());
    }

    sortUpdated(newField, newOrder){
        console.log("sortUpdated: ", newField, newOrder);

        this.setState({sortField: newField, sortOrder:newOrder},()=>this.doSort());
    }

    render(){
        return <div>
            <h1>Fibre Census</h1>

            <div className="centered">
                <label htmlFor="search-box">Search:</label>
                <input type="text" id="search-box" style={{width: "50%"}} onChange={this.updateSearchTerms}/>
                <img style={{marginLeft:"auto",marginRight:"auto",width:"44px", display: this.state.loading ? "inline" : "none" }} src="/assets/images/Spinner-1s-44px.svg"/>
                <input type="checkbox" checked={this.state.showDriverDetails} id="driver-details-check" onChange={event=>this.setState({showDriverDetails: !this.state.showDriverDetails})}/>
                <label htmlFor="driver-details-check">Show driver details</label><br/>
                <label>Showing</label><ProblemsFilter onChange={evt=>this.setState({currentFilter: evt.target.value})} value={this.state.currentFilter}/>
                <span className="informative">{
                    this.state.filteredHitsCount===this.state.data.length ? "Showing " + this.state.data.length + " reports" : "Showing " + this.state.filteredHitsCount + " from a total of " + this.state.data.length + " reports"
                }</span><br/>
                <label>Sorted by</label><SortSelector value={this.state.sortField} order={this.state.sortOrder} onChange={this.sortUpdated}/>
            </div>

            <ul className="boxlist">
                {
                    this.state.data.map(entry=><li key={entry.hostName} className={"entry-container " + entry.validation} style={{display: this.state.currentFilter==="all" || this.state.currentFilter===entry.validation ? "block" : "none"}}>
                        <span className="entry-header">{entry.hostName} - <span className="entry-header-additional"><TimestampFormatter relative={this.state.showRelativeTime} value={entry.lastUpdate}/></span></span>
                        <DisplayFibreDrivers title="Fibre drivers present"
                                             listData={entry.driverInfo}
                                             showDetails={this.state.showDriverDetails}
                                             validationComponent={<ValidateFibreDrivers listData={entry.driverInfo}/>}
                                             extraClasses="oversized float-right"
                        />
                        <DisplaySimpleText title="Model" entry={entry.model} validationComponent={<ValidateModel stringData={entry.model}/>}/>
                        <DisplaySimpleText title="Computer Name" entry={entry.computerName} extraClasses="wider"/>
                        <DisplayTextList title="IP Addresses"
                                         bulletIcon="network-wired"
                                         listData={entry.ipAddresses}
                                         validationComponent={<ValidateIpAddresses listData={entry.ipAddresses}/>}
                        />
                        <DisplayRecentUsers title="Recent Users" entry={entry} extraClasses="doublewidth"/>
                        <DisplayTextList title="Fibre WWNs" listData={entry.fcWWN} extraClasses="wider" validationComponent={<ValidateFCWWN listData={entry.fcWWN}/>}/>
                        <DisplayTextList title="Fibre status" listData={entry.fcStatus}/>
                        <DisplayTextList title="Fibre speed" listData={entry.fcSpeed}/>
                        <DisplayTextList title="LUN count" listData={entry.fcLunCount} validationComponent={<ValidateLunCount listData={entry.fcLunCount}/>}/>
                        <DisplayTextList title="DenyDLC"
                                         bulletIcon="hdd"
                                         listData={entry.denyDlcVolumes}
                                         validationComponent={<ValidateSanVolumes listData={entry.denyDlcVolumes}/>}
                        />
                        <DisplaySimpleText title="Fibre adaptor model" entry={entry.fcAdaptor} extraClasses="wider"/>
                        <DisplayMdcPing title="MDC Controller Connectivity"
                                        listData={entry.mdcPing}
                                        extraClasses="doublewidth"
                                        validationComponent={<ValidateMdcPing listData={entry.mdcPing}/>}
                        />
                        <DisplayTextList title="SAN mounts"
                                         bulletIcon="hdd"
                                         listData={entry.sanMounts ? entry.sanMounts.map(m=>m.name) : null}
                                         validationComponent={<ValidateSanVolumes listData={entry.sanMounts ? entry.sanMounts.map(m=>m.name) : null}/>}
                        />
                    </li> )
                }

            </ul>
            <ReactTooltip/>
        </div>
    }
}

export default NewFrontPage;
