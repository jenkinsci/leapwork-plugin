

function GetSch() {
    var closestElement = $(event.target).closest('.repeated-chunk');
    if (closestElement != null) { var closestEle = closestElement.firstElementChild }
    else { closestEle = document }

    const leapworkHostname = closestEle.getElementsBySelector("#leapworkHostname").first().value;
    const leapworkPort = closestEle.getElementsBySelector("#leapworkPort").first().value;

    if (!leapworkHostname || !leapworkPort) {
        alert('"hostname or/and field is empty! Cannot connect to controller!"');
    }
    else {
        let myAddress = "";
        if(closestEle.getElementsBySelector("#chkEnableHttps").first().checked){myAddress = "https://" + leapworkHostname + ":" + leapworkPort;}
        else{myAddress = "http://" + leapworkHostname + ":" + leapworkPort;}
        const address = myAddress;
        const accessKey = closestEle.getElementsBySelector("#leapworkAccessKey").first().value;

        if (closestEle.getElementsBySelector('#LeapworkContainer').first().innerHTML == "") {

            (jQuery).ajax({
                url: address + "/api/v4/schedules/hierarchy",
                headers: { 'AccessKey': accessKey },
                type: 'GET',
                dataType: "json",
                success: function (json) {
                    const container = closestEle.getElementsBySelector('#LeapworkContainer').first();


                    (jQuery)(document).click(function (event) {
                        if ((jQuery)(event.target).closest('#LeapworkContainer').length == 0 && (jQuery)(event.target).attr('id') != 'mainButton') {
                            (jQuery)("#LeapworkContainer input:checkbox").remove();
                            (jQuery)("#LeapworkContainer li").remove();
                            (jQuery)("#LeapworkContainer ul").remove();
                            (jQuery)("#LeapworkContainer br").remove();
                            (jQuery)("#LeapworkContainer div").remove();
                            (jQuery)("#LeapworkContainer input").remove();
                            container.style.display = 'none';
                        }
                    });

                    const scheduleInfo = json.map((_) => _.ScheduleInfo);
                    const scheduleHierarchies = json.map((_) => _.ScheduleHierarchy);
                    createFolders(scheduleHierarchies, scheduleInfo, container);

                    function createFolders(ScheduleHeirarchies, scheduleInfo, root) {

                        for (const ScheduleHierarchy of ScheduleHeirarchies) {

                            const title = ScheduleHierarchy["Title"]
                            if (
                                ScheduleHierarchy["Type"] == "Folder" &&
                                ScheduleHierarchy.Parent.length == 0
                            ) {
                                if (!document.getElementById(`${ScheduleHierarchy.Id}`))
                                    root.innerHTML += `<div id="${ScheduleHierarchy.Id}" class="Folder" style="color: red;">${title}</div>`;
                            } else if (
                                ScheduleHierarchy["Type"] == "Folder" &&
                                ScheduleHierarchy.Parent.length
                            ) {
                                if (!document.getElementById(`${ScheduleHierarchy.Parent[0].Id}`))
                                    createFolders(ScheduleHierarchy.Parent, scheduleInfo, root);

                                let myParent = document.getElementById(
                                    `${ScheduleHierarchy.Parent[0].Id}`
                                );
                                myParent.innerHTML += `<div id="${ScheduleHierarchy.Id}" class="Folder" style="color: darkblue; padding-left:20px;">${title}</div>`;
                            } 
                            
                            else if (ScheduleHierarchy["Type"] == "RunList" &&
                            		 ScheduleHierarchy.Parent.length == 0) {
                                if (!document.getElementById(`${ScheduleHierarchy.Id}`))
                                root.innerHTML += `<div id="${ScheduleHierarchy.Id}" class="RunList" style="color: black; padding-left:20px;">${title}</div>`;
                            } 
 
                            else if (ScheduleHierarchy["Type"] == "RunList") {
                                if (!document.getElementById(`${ScheduleHierarchy.Parent[0].Id}`))
                                    createFolders(ScheduleHierarchy.Parent, scheduleInfo, root);

                                let myParent = document.getElementById(
                                    `${ScheduleHierarchy.Parent[0].Id}`
                                );
                                myParent.innerHTML += `<div id="${ScheduleHierarchy.Id}" class="RunList" style="color: black; padding-left:20px;">${title}</div>`;
                            } else if (ScheduleHierarchy["Type"] == "ScheduleInfo") {
                                var scheduleId = ScheduleHierarchy.Id;
                                if (!document.getElementById(`${ScheduleHierarchy.Parent[0].Id}`))
                                    createFolders(
                                        ScheduleHierarchy.Parent,
                                        scheduleInfo,
                                        container
                                    );

                                let myParent = document.getElementById(
                                    `${ScheduleHierarchy.Parent[0].Id}`
                                );

                                for (schedule of scheduleInfo) {

                                    if (schedule.Id == scheduleId) {
                                        if (schedule.IsEnabled) {
                                            myParent.innerHTML += `<div style="color: green; padding-left:30px;">
                                       <input type = "checkBox" id="${ScheduleHierarchy.Id}" class="ScheduleInfo" name="${title}">${title}</div>`;
                                        }
                                        else {
                                            var element = `<div style="color:grey; padding-left:30px;">
                                        <input type = "checkBox" disabled = "true" id="${ScheduleHierarchy.Id}" class="ScheduleInfoDisabled" name="${title}"><s>${title}</s></div>`;
                                            myParent.innerHTML += element
                                        }

                                    }
                                }

                            }
                        }
                    }


                    container.innerHTML += '<br>';


                    container.innerHTML += '<br>';

                    container.style.display = 'block';

                    (jQuery)(".ul-dropfree").find("li:has(ul)").prepend('<div class="drop"></div>');
                    (jQuery)(".ul-dropfree div.drop").click(function () {
                        if ((jQuery)(this).nextAll("ul").css('display') == 'none') {
                            (jQuery)(this).nextAll("ul").slideDown(400);
                            (jQuery)(this).css({ 'background-position': "-11px 0" });
                        } else {
                            (jQuery)(this).nextAll("ul").slideUp(400);
                            (jQuery)(this).css({ 'background-position': "0 0" });
                        }
                    });
                    (jQuery)(".ul-dropfree").find("ul").slideUp(400).parents("li").children("div.drop").css({ 'background-position': "0 0" });

                    let TestNames = closestEle.getElementsBySelector("#schNames").first();
                    let TestIds = closestEle.getElementsBySelector("#schIds").first();

                    let boxes = closestEle.getElementsBySelector("#LeapworkContainer input:checkbox");
                    let existingTests = new Array();
                    existingTests = TestIds.value.split(/\r\n|\n|\s+,\s+|,\s+|\s+,|,/);

                    if (TestNames.value != null && TestIds.value != null) {
                        for (let i = 0; i < existingTests.length; i++) {
                            for (j = 0; j < boxes.length; j++) {

                                if (existingTests[i] == boxes[j].getAttributeNode('id').value) {
                                    if (boxes[j].disabled == false)
                                        (jQuery)(boxes[j]).prop('checked', 'checked');

                                }
                            }
                        }

                    }

                    (jQuery)(closestEle.getElementsBySelector("#LeapworkContainer input:checkbox")).on("change", function () {
                        let NamesArray = new Array();
                        let IdsArray = new Array();
                        for (let i = 0; i < boxes.length; i++) {
                            let box = boxes[i];
                            if ((jQuery)(box).prop('checked')) {
                                NamesArray[NamesArray.length] = (jQuery)(box).attr('name');
                                IdsArray[IdsArray.length] = (jQuery)(box).attr('id');
                            }
                        }
                        TestNames.value = NamesArray.join("\n");
                        TestIds.value = IdsArray.join("\n");
                        console.log(TestIds.value)
                    });

                },
                error: function (XMLHttpRequest, textStatus, errorThrown) {
                    alert(
                        "Error occurred! Cannot get the list of Schedules!\n" +
                        "Status: " + textStatus + "\n" +
                        "Error: " + errorThrown + "\n" +
                        "This may occur because of the next reasons:\n" +
                        "1.Invalid controller hostname\n" +
                        "2.Invalid port number\n" +
                        "3.Invalid access key\n" +
                        "4.Controller is not running or updating now, check it in services\n" +
                        "5.Your Leapwork Controller API port is blocked.\nUse 'netstat -na | find \"9001\"' command, The result should be:\n 0.0.0.0:9001  0.0.0.0:0  LISTENING\n" +
                        "6.Your browser has such a setting enabled that blocks any http requests from https\n" +
                        "If nothing helps, please contact support https://leapwork.com/support"
                    );
                }
            });
        }
        else {
            (jQuery)("#LeapworkContainer input:checkbox").remove();
            (jQuery)("#LeapworkContainer li").remove();
            (jQuery)("#LeapworkContainer ul").remove();
            (jQuery)("#LeapworkContainer br").remove();
            (jQuery)("#LeapworkContainer div").remove();
            (jQuery)("#LeapworkContainer input").remove();
            GetSch();
        }

    }
}
